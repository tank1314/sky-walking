package com.a.eye.skywalking.routing.router;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.registry.api.NotifyListener;
import com.a.eye.skywalking.registry.api.RegistryNode;
import com.a.eye.skywalking.routing.client.StorageClientCachePool;
import com.a.eye.skywalking.routing.disruptor.NoopSpanDisruptor;
import com.a.eye.skywalking.routing.disruptor.SpanDisruptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class Router implements NotifyListener {
    private static ILog logger = LogManager.getLogger(Router.class);

    private ReentrantLock   lock       = new ReentrantLock();
    private SpanDisruptor[] disruptors = new SpanDisruptor[0];

    public SpanDisruptor lookup(RequestSpan requestSpan) {
        return getSpanDisruptor(requestSpan.getRouteKey());
    }

    public SpanDisruptor lookup(AckSpan ackSpan) {
        return getSpanDisruptor(ackSpan.getRouteKey());
    }

    private SpanDisruptor getSpanDisruptor(long routKey) {
        if (disruptors.length == 0) {
            return NoopSpanDisruptor.INSTANCE;
        }

        while (true) {
            int index = Math.abs((int) (routKey % disruptors.length));
            try {
                return disruptors[index];
            } catch (ArrayIndexOutOfBoundsException e) {
            }
        }
    }

    @Override
    public void notify(List<RegistryNode> registryNodes) {
        try {
            lock.lock();
            List<SpanDisruptor> newDisruptors = new ArrayList<SpanDisruptor>(Arrays.asList(disruptors));
            List<SpanDisruptor> removedDisruptors = new ArrayList<SpanDisruptor>();

            for (RegistryNode node : registryNodes) {
                if (node.getChangeType() == RegistryNode.ChangeType.ADDED) {
                    newDisruptors.add(new SpanDisruptor(node.getNode()));
                } else {
                    removedDisruptors.add(getAndRemoveSpanDistruptor(newDisruptors, node.getNode()));
                }
            }

            Collections.sort(newDisruptors, (o1, o2) -> {
                long o1Key = Long.parseLong(o1.getConnectionURL().replace(".", "").replace(":", ""));
                long o2Key = Long.parseLong(o2.getConnectionURL().replace(".", "").replace(":", ""));

                if (o1Key == o2Key) {
                    return 0;
                } else if (o1Key > o2Key) {
                    return 1;
                } else {
                    return -1;
                }
            });

            //先停止往里面存放数据
            disruptors = newDisruptors.toArray(new SpanDisruptor[newDisruptors.size()]);

            // 而后stop
            for (SpanDisruptor removedDisruptor : removedDisruptors) {
                removedDisruptor.shutdown();
                StorageClientCachePool.INSTANCE.shutdown(removedDisruptor.getConnectionURL());
            }
        } finally {
            lock.unlock();
        }
    }

    private SpanDisruptor getAndRemoveSpanDistruptor(List<SpanDisruptor> newDisruptors, String connectionURL) {
        return newDisruptors.remove(newDisruptors.indexOf(new SpanDisruptor(connectionURL)));
    }

    public void stop() {
        logger.info("Stopping routing service.");
        for (SpanDisruptor disruptor : disruptors) {

        }
    }
}
