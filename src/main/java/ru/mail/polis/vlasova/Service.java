package ru.mail.polis.vlasova;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.mail.polis.KVService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.*;

public class Service implements KVService {
    private static final String STATUS_PATH = "/v0/status";
    private static final String ENTITY_PATH = "/v0/entity";
    private static final String INNER_PATH = "/v0/inner";

    private static final String NOT_FOUND_MESSAGE = "Not found";
    private static final String BAD_REQUEST_MESSAGE = "Bad request";

    private static final int OK = 200;
    private static final int CREATED = 201;
    private static final int ACCEPTED = 202;
    private static final int NOT_FOUND = 404;
    private static final int BAD_REQUEST = 400;
    private static final int NOT_ENOUGH_REPLICAS = 504;
    private static final int INTERNAL_SEVER_ERROR = 500;

    @NotNull
    private final HttpServer server;
    @NotNull
    private final FileDao dao;
    @NotNull
    private final List<String> topology;
    private final int port;

    public Service(int port, @NotNull final FileDao dao, @NotNull final Set<String> topology) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.dao = dao;
        this.topology = new ArrayList<>(topology);
        this.port = port;

        server.createContext(STATUS_PATH, createStatusHandler());
        server.createContext(INNER_PATH, createInnerHandler());
        server.createContext(ENTITY_PATH, createEntityHandler());
    }

    @Override
    public void start() {
        server.start();
    }

    @Override
    public void stop() {
        server.stop(0);
    }

    private void sendResponse(@NotNull HttpExchange http, int code, byte[] response) throws IOException {
        http.sendResponseHeaders(code, response.length);
        http.getResponseBody().write(response);
        http.close();
    }

    private void sendResponse(@NotNull HttpExchange http, int code) throws IOException {
        http.sendResponseHeaders(code, 0);
        http.close();
    }

    @NotNull
    private HttpHandler createStatusHandler() throws IOException {
        return http -> sendResponse(http, OK);
    }

    @NotNull
    private HttpHandler createInnerHandler() throws IOException {
        return http -> {
            try {
                final String id = extractID(http.getRequestURI().getQuery());
                if (id.isEmpty()) {
                    sendResponse(http, BAD_REQUEST, BAD_REQUEST_MESSAGE.getBytes());
                    return;
                }
                switch (http.getRequestMethod()) {
                    case "GET":
                        final byte[] value;
                        value = dao.get(id);
                        sendResponse(http, OK, value);
                        break;
                    case "DELETE":
                        dao.delete(id);
                        sendResponse(http, ACCEPTED);
                        break;
                    case "PUT":
                        dao.upsert(id, readPutValue(http));
                        sendResponse(http, CREATED);
                        break;
                    default:
                        sendResponse(http, BAD_REQUEST, BAD_REQUEST_MESSAGE.getBytes());
                }
            } catch (NoSuchElementException e) {
                sendResponse(http, NOT_FOUND, NOT_FOUND_MESSAGE.getBytes());
            } catch (IllegalArgumentException e) {
                sendResponse(http, BAD_REQUEST, BAD_REQUEST_MESSAGE.getBytes());
            }
        };
    }

    @NotNull
    private HttpHandler createEntityHandler() throws IOException {
        return http -> {
            try {
                final String query = http.getRequestURI().getQuery();
                final String id = extractID(query);
                final int ack = extractACK(query);
                final int from = extractFROM(query);
                if (id.isEmpty() || ack > from || ack == 0 || from == 0) {
                    sendResponse(http, BAD_REQUEST, BAD_REQUEST_MESSAGE.getBytes());
                    return;
                }
                switch (http.getRequestMethod()) {
                    case "GET":
                        handleEntityGet(http, id, ack, from);
                        break;
                    case "PUT":
                        handleEntityPut(http, id, ack, from, readPutValue(http));
                        break;
                    case "DELETE":
                        handleEntityDelete(http, id, ack, from);
                        break;
                    default:
                        sendResponse(http, BAD_REQUEST, BAD_REQUEST_MESSAGE.getBytes());
                }
            } catch (IllegalArgumentException e) {
                sendResponse(http, BAD_REQUEST, BAD_REQUEST_MESSAGE.getBytes());
            }
        };
    }

    private void handleEntityGet(@NotNull HttpExchange http, @NotNull String id, int ack, int from)
            throws IOException, IllegalArgumentException {
        int ok = 0;
        int not_found = 0;
        byte[] data = null;
        for (String node : getNodes(id, from)) {
            if (node.equals("http://localhost:" + port)) {
                try {
                    data = dao.get(id);
                    ok++;
                } catch (NoSuchElementException e) {
                    not_found++;
                }
                continue;
            }
            int code;
            try {
                URL url = new URL(node + INNER_PATH + "?id=" + id);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();
                code = connection.getResponseCode();
                try {
                    InputStream is = connection.getInputStream();
                    data = readInputStreamData(is);
                } catch (IOException e) {
                    not_found++;
                    continue;
                }
                connection.disconnect();
            } catch (Exception e) {
                continue;
            }
            if (code == OK) {
                ok++;
            }
            if (code == NOT_FOUND) {
                not_found++;
            }
        }
        if (ok > 0 && not_found == 1 && !checkGet(id)) {
            ok++;
            not_found--;
        }
        if (ok + not_found < ack) {
            sendResponse(http, NOT_ENOUGH_REPLICAS);
        }
        else if (ok < ack) {
            sendResponse(http, NOT_FOUND);
        }
        else {
            sendResponse(http, OK, data);
        }
    }

    @NotNull
    private byte[] readInputStreamData(@NotNull InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int count;
        while ((count = is.read(data)) != -1) {
            baos.write(data, 0, count);
        }
        return data;
    }

    private void handleEntityDelete(@NotNull HttpExchange http, @NotNull String id, int ack, int from) throws IOException {
        int ok = 0;
        for (String node : getNodes(id, from)) {
            if (node.equals("http://localhost:" + port)) {
                try {
                    dao.delete(id);
                } catch (IOException e) {}
                ok++;
                continue;
            }
            String url = node + INNER_PATH + "?id=" + id;
            if (getDeleteResponse(url) == ACCEPTED) {
                ok++;
            }
        }
        if (ok >= ack) {
            sendResponse(http, ACCEPTED);
        }
        else {
            sendResponse(http, NOT_ENOUGH_REPLICAS);
        }
    }

    private int getDeleteResponse(@NotNull String addr) {
        try{
            URL url = new URL(addr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("DELETE");
            connection.connect();
            int code = connection.getResponseCode();
            connection.disconnect();
            return code;
        } catch (Exception e) {
            return INTERNAL_SEVER_ERROR;
        }
    }

    private void handleEntityPut(@NotNull HttpExchange http, @NotNull String id, int ack, int from,
                                 @NotNull byte[] value) throws IOException {
        int ok = 0;
        for (String node : getNodes(id, from)) {
            if (node.equals("http://localhost:" + port)) {
                try {
                    dao.upsert(id, value);
                    ok++;
                } catch (IOException e) {}
                continue;
            }
            String url = node + INNER_PATH + "?id=" + id;
            if (getPutResponse(url, value) == 201) {
                ok++;
            }
        }
        if (ok >= ack) {
            sendResponse(http, CREATED);
        }
        else sendResponse(http, NOT_ENOUGH_REPLICAS);
    }

    private int getPutResponse(@NotNull String addr, @NotNull byte[] data) {
        try {
            URL url = new URL(addr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);
            connection.connect();
            OutputStream os = connection.getOutputStream();
            os.write(data);
            os.flush();
            os.close();
            int code = connection.getResponseCode();
            connection.disconnect();
            return code;
        } catch (Exception e) {
            return INTERNAL_SEVER_ERROR;
        }
    }

    @NotNull
    private String extractID(@NotNull String query) throws IllegalArgumentException {
        Map<String, String> params = queryToMap(query);
        String id = params.get("id");
        if (id == null) {
            throw new IllegalArgumentException();
        }
        return id;
    }

    private int extractACK(@NotNull String query) {
        String ack = parseReplicas(query);
        if (ack == null) {
            return topology.size() / 2 + 1;
        }
        return Integer.valueOf(ack.split("/")[0]);
    }

    private int extractFROM(@NotNull String query) {
        String from = parseReplicas(query);
        if (from == null) {
            return topology.size();
        }
        return Integer.valueOf(from.split("/")[1]);
    }

    @Nullable
    private String parseReplicas(@NotNull String query) throws IllegalArgumentException {
        Map<String, String> params = queryToMap(query);
        if (params.isEmpty()) {
            throw new IllegalArgumentException();
        }
        return params.get("replicas");
    }

    @NotNull
    private Map<String, String> queryToMap(@NotNull final String query) throws IllegalArgumentException {
        Map<String, String> result = new HashMap<>();
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 1) {
                throw new IllegalArgumentException();
            }
            result.put(pair[0], pair[1]);
        }
        return result;
    }

    @NotNull
    private byte[] readPutValue(@NotNull HttpExchange http) throws IOException, IllegalArgumentException {
        Headers headers = http.getRequestHeaders();
        if (headers == null) {
            throw  new IllegalArgumentException();
        }
        int length = Integer.valueOf(headers.getFirst("Content-Length"));
        final byte[] putValue = new byte[length];
        http.getRequestBody().read(putValue);
        return putValue;
    }

    @NotNull
    private List<String> getNodes(@NotNull String id, int from) {
        List<String> nodes = new ArrayList<>();
        int index = (Math.abs(id.hashCode() % topology.size())) - 1;
        for (int i = 0; i < from; i++) {
            index = (index + 1) % topology.size();
            nodes.add(topology.get(index));
        }
        return nodes;
    }

    private boolean checkGet(@NotNull String id) {
        try {
            dao.get(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

