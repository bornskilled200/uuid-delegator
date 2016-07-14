package com.unseenspace;

import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.http.HttpServer;

import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Delegator {

    public static void main(String[] args) {
        int processors = Runtime.getRuntime().availableProcessors();
        BlockingQueue<String> uuids = new LinkedBlockingQueue<>();
        Collections.addAll(uuids, args);
        System.out.println(uuids);


        Vertx vertx = Vertx.vertx();
        WorkerExecutor consumers = vertx.createSharedWorkerExecutor("consumers", processors, TimeUnit.MINUTES.toMillis(2));
        vertx
            .createHttpServer()
            .requestHandler(req -> {
                if (!req.path().equals("/")) {
                    req.response().setStatusCode(400).end();
                    return;
                }

                switch (req.method()) {
                    case GET:
                        consumers.<String>executeBlocking(
                                future -> {
                                    String poll = uuids.poll();
                                    if (poll == null)
                                        future.failed();
                                    else
                                        future.complete(poll);
                                    System.out.println(uuids);
                                },
                                result -> req.response().end(result.result()));
                        break;
                    case POST:
                        req.setExpectMultipart(true);
                        req.endHandler((ignored) -> {
                            String uuid = req.getFormAttribute("uuid");
                            if (uuid != null && !uuid.isEmpty()) {
                                uuids.offer(uuid);
                                req.response().end("added");
                            } else
                                req.response().setStatusCode(400).end("no uuid: " + uuid);
                        });
                        break;
                    case OPTIONS:
                        req.response().putHeader("Access-Control-Allow-Origin", "*").end();
                        break;
                }
            })
            .listen(handler -> {
                HttpServer server = handler.result();
                if (handler.succeeded()) {
                    int i = server.actualPort();
                    System.out.println("http://localhost:" + i + "/");
                } else {
                    System.err.println("Failed to listen on port 8080");
                    server.close();
                }
            });
    }

}