
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.http.HttpServer;

import java.util.Collections;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Test {

    public static void main(String[] args) {
        int processors = Runtime.getRuntime().availableProcessors();
        BlockingQueue<String> uuids = new LinkedBlockingQueue<>();
        Collections.addAll(uuids, "hi", "bye");
        System.out.println(uuids);
        // Create an HTTP server which simply returns "Hello World!" to each request.
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
                        req.response().putHeader("Accept", "*").end();
                        break;
                }
            })
            .listen(handler -> {
                if (handler.succeeded()) {
                    int i = handler.result().actualPort();
                    System.out.println("http://localhost:" + i + "/");
                } else {
                    System.err.println("Failed to listen on port 8080");
                }
            });

        try (Scanner scanner = new Scanner(System.in)) {
            scanner.nextLine();
        }
    }

}