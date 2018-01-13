package ru.mail.polis.vlasova;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;
import ru.mail.polis.KVService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.NoSuchElementException;

public class Service implements KVService {
    private static final String STATUS_PATH = "/v0/status";
    private static final String ENTITY_PATH = "/v0/entity";
    private static final String PREFIX = "id=";

    private static final String NOT_FOUND_MESSAGE = "Not found";
    private static final String BAD_REQUEST_MESSAGE = "Bad request";

    private static final int OK = 200;
    private static final int CREATED = 201;
    private static final int ACCEPTED = 202;
    private static final int NOT_FOUND = 404;
    private static final int BAD_REQUEST = 400;

    @NotNull
    private final HttpServer server;
    @NotNull
    private final FileDao dao;

    public Service(int port, @NotNull final FileDao dao) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.dao = dao;

        server.createContext(STATUS_PATH, createStatusHandler());
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

    private void sendResponse(@NotNull HttpExchange http, int code, @NotNull byte[] response) throws IOException {
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
    private HttpHandler createEntityHandler() throws IOException {
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
    private String extractID(@NotNull final String query) throws IllegalArgumentException {
        if (!query.startsWith(PREFIX)) {
            throw new IllegalArgumentException();
        }
        return query.substring(PREFIX.length());
    }

    @NotNull
    private byte[] readPutValue(HttpExchange http) throws IOException, IllegalArgumentException {
        Headers headers = http.getRequestHeaders();
        if (headers == null) {
            throw  new IllegalArgumentException();
        }
        int length = Integer.valueOf(headers.getFirst("Content-Length"));
        final byte[] putValue = new byte[length];
        http.getRequestBody().read(putValue);
        return putValue;
    }
}

