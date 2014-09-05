package co.cask.coopr.common.queue.guice;

import co.cask.coopr.common.conf.Constants;
import co.cask.coopr.common.queue.QueueGroup;
import co.cask.coopr.common.queue.QueueType;
import co.cask.coopr.common.queue.TrackingQueue;
import co.cask.coopr.common.queue.internal.ElementsTrackingQueue;
import co.cask.coopr.common.queue.internal.ZKElementsTracking;
import co.cask.coopr.common.queue.internal.ZKQueueGroup;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import org.apache.twill.zookeeper.ZKClient;

/**
 * Guice module for binding queue related classes and instances.
 */
public class QueueModule extends AbstractModule {
  private final ZKClient zkClient;

  public QueueModule(ZKClient zkClient) {
    this.zkClient = zkClient;
  }

  @Override
  protected void configure() {
    ElementsTrackingQueue balancerQueue =
      new ElementsTrackingQueue(new ZKElementsTracking(zkClient, QueueType.BALANCER.getPath()));

    bind(ElementsTrackingQueue.class)
      .annotatedWith(Names.named(Constants.Queue.WORKER_BALANCE)).toInstance(balancerQueue);
    bind(TrackingQueue.class)
      .annotatedWith(Names.named(Constants.Queue.WORKER_BALANCE)).toInstance(balancerQueue);

    bind(QueueGroup.class).annotatedWith(Names.named(Constants.Queue.CALLBACK))
      .toInstance(new ZKQueueGroup(zkClient, QueueType.CALLBACK));
    bind(QueueGroup.class).annotatedWith(Names.named(Constants.Queue.CLUSTER))
      .toInstance(new ZKQueueGroup(zkClient, QueueType.CLUSTER));
    bind(QueueGroup.class).annotatedWith(Names.named(Constants.Queue.JOB))
      .toInstance(new ZKQueueGroup(zkClient, QueueType.JOB));
    bind(QueueGroup.class).annotatedWith(Names.named(Constants.Queue.PROVISIONER))
      .toInstance(new ZKQueueGroup(zkClient, QueueType.PROVISIONER));
    bind(QueueGroup.class).annotatedWith(Names.named(Constants.Queue.SOLVER))
      .toInstance(new ZKQueueGroup(zkClient, QueueType.SOLVER));
  }
}
