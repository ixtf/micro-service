package test;

import com.github.ixtf.api.ApiLauncher;
import com.github.ixtf.api.MainVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import reactor.tools.agent.ReactorDebugAgent;

@Slf4j
public class ApiDebug extends ApiLauncher {

    public static void main(String[] args) {
        ReactorDebugAgent.init();

        System.setProperty("vertx.hazelcast.config", "/data/api/cluster.xml");
        System.setProperty("hazelcast.local.publicAddress", "172.10.10.111");
        new ApiDebug().dispatch(new String[]{"-cluster"});

//        new ApiDebug().dispatch(args);
    }

    @Override
    protected String getMainVerticle() {
        return MainVerticle.class.getName();
    }

    @Override
    public void afterStartingVertx(Vertx vertx) {
        super.afterStartingVertx(vertx);

        vertx.eventBus().consumer("test:test", reply -> {
            final var ret = new JsonObject().put("test", "test");
            reply.reply(ret.encode());
        });
    }

}
