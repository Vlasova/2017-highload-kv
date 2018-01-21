## Нагрузочное тестирование

Тестирование проводилось с помошью curl. Используемые скрипты находятся в папке /scripts.

### До оптимизации

GET без повторов, replicas=2/3
```
$ ./get1.sh 3000 2 3
Average delay: 26.795 ms
Requests/sec: 37.3
```
GET без повторов, replicas=3/3
```
$ ./get1.sh 3000 3 3
Average delay: 27.266 ms
Requests/sec: 36.6
```
GET с повторами, replicas=2/3
```
$ ./get2.sh 3000 2 3
Average delay: 26.982 ms
Requests/sec: 37.0
```
GET с повторами, replicas=3/3
```
$ ./get2.sh 3000 3 3
Average delay: 27.193 ms
Requests/sec: 36.7
```
PUT без перезаписи, replicas=2/3
```
$ ./put1.sh 3000 2 3
Average delay: 32.444 ms
Requests/sec: 30.8
```
PUT без перезаписи, replicas=3/3
```
$ ./put1.sh 3000 3 3
Average delay: 28.942 ms
Requests/sec: 34.5
```
PUT с перезаписью, replicas=2/3
```
$ ./put2.sh 3000 2 3
Average delay: 29.465 ms
Requests/sec: 33.9
```
PUT с перезаписью, replicas=3/3
```
$ ./put2.sh 3000 3 3
Average delay: 29.242 ms
Requests/sec: 34.1
```

### После оптимизации

GET без повторов, replicas=2/3
```
$ ./get1.sh 3000 2 3
Average delay: 25.716 ms
Requests/sec: 38.8
```
GET без повторов, replicas=3/3
```
$ ./get1.sh 3000 3 3
Average delay: 24.644 ms
Requests/sec: 40.5
```
GET с повторами, replicas=2/3
```
$ ./get2.sh 3000 2 3
Average delay: 22.840 ms
Requests/sec: 43.7
```
GET с повторами, replicas=3/3
```
$ ./get2.sh 3000 3 3
Average delay: 22.757 ms
Requests/sec: 43.9
```
PUT без перезаписи, replicas=2/3
```
$ ./put1.sh 3000 2 3
Average delay: 27.980 ms
Requests/sec: 35.7
```
PUT без перезаписи, replicas=3/3
```
$ ./put1.sh 3000 3 3
Average delay: 27.778 ms
Requests/sec: 35.9
```
PUT с перезаписью, replicas=2/3
```
$ ./put2.sh 3000 2 3
Average delay: 26.588 ms
Requests/sec: 37.6
```
PUT с перезаписью, replicas=3/3
```
$ ./put2.sh 3000 3 3
Average delay: 24.958 ms
Requests/sec: 40.0
```
