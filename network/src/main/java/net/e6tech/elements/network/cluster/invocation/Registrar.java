/*
 * Copyright 2015-2019 Futeh Kao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.e6tech.elements.network.cluster.invocation;


import akka.actor.Status;
import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import net.e6tech.elements.common.actor.Genesis;
import net.e6tech.elements.common.resources.NotAvailableException;
import scala.concurrent.ExecutionContextExecutor;

import java.util.*;

import static net.e6tech.elements.network.cluster.invocation.InvocationEvents.*;

public class Registrar extends AbstractBehavior<InvocationEvents> {

    private Map<ServiceKey, ActorRef<InvocationEvents.Request>> routes = new HashMap<>(); // key is the context@method
    private Map<ServiceKey, Set<ActorRef<InvocationEvents.Request>>> actors = new HashMap<>();
    private Map<ActorRef<InvocationEvents.Request>, ServiceKey> actorKeys = new HashMap<>();
    private RegistryImpl registry;
    private ActorContext<InvocationEvents> context;

    public Registrar(ActorContext<InvocationEvents> context, RegistryImpl registry) {
        this.context = context;
        this.registry = registry;
    }

    public ActorContext<InvocationEvents> getContext() {
        return context;
    }

    public void setContext(ActorContext<InvocationEvents> context) {
        this.context = context;
    }

    @Override
    public Receive<InvocationEvents> createReceive() {
        return newReceiveBuilder()
                .onMessage(Registration.class, this::registration)
                .onMessage(Request.class, this::request)
                .onMessage(Routes.class, this::routes)
                .onSignal(Terminated.class, this::terminated)
                .build();
    }

    // spawn a RegistryEntry
    private Behavior<InvocationEvents> registration(Registration registration) {
        String dispatcher;
        ExecutionContextExecutor executor = context.getSystem().dispatchers().lookup(DispatcherSelector.fromConfig(RegistryImpl.REGISTRY_DISPATCHER));
        if (executor != null) {
            dispatcher = RegistryImpl.REGISTRY_DISPATCHER;
        } else {
            dispatcher = Genesis.WORKER_POOL_DISPATCHER;
        }

        // spawn a child to listen for RegistryEntry
        ServiceKey<InvocationEvents.Request> key = ServiceKey.create(InvocationEvents.Request.class, registration.getPath());
        context.spawnAnonymous(Behaviors.setup(
                ctx -> {
                    ctx.getSystem().receptionist().tell(Receptionist.subscribe(key, ctx.getSelf().narrow()));
                    return Behaviors.receive(Object.class)
                            .onMessage(Receptionist.Listing.class,
                            (c, msg) -> {
                                Set<ActorRef<InvocationEvents.Request>> set = actors.getOrDefault(key, Collections.emptySet());
                                for (ActorRef<InvocationEvents.Request> ref : msg.getServiceInstances(key)) {
                                    if (!set.contains(ref)) {
                                        context.watch(ref); // watch for Terminated event
                                        actorKeys.put(ref, key);
                                        registry.onAnnouncement(key.id());
                                    }
                                }
                                actors.put(key, new LinkedHashSet<>(msg.getServiceInstances(key)));
                                return Behaviors.same();
                            })
                    .build();
                }
        ));

        context.spawnAnonymous(
                        Behaviors.<InvocationEvents.Request>setup(c -> {
                            c.getSystem().receptionist().tell(Receptionist.register(key, c.getSelf()));
                            return new RegistryEntry(c, registry.genesis(), registration);
                        }),
                        Props.empty().withDispatcherFromConfig(dispatcher));

        routes.computeIfAbsent(key,
                k -> {
                    GroupRouter<InvocationEvents.Request> g = Routers.group(key);
                    ActorRef<InvocationEvents.Request> router = context.spawnAnonymous(g);
                    return router;
                });

        return Behaviors.same();
    }

    // Forward request to router
    private Behavior<InvocationEvents> request(Request request) {
        ServiceKey key = ServiceKey.create(InvocationEvents.Request.class, request.getPath());
        ActorRef<InvocationEvents.Request> router = routes.get(key);
        if (router == null) {
            request.getSender().tell(new Status.Failure(new NotAvailableException("Service not available.")));
        } else {
            router.tell(request);
        }

        return Behaviors.same();
    }

    private Behavior<InvocationEvents> terminated(Terminated terminated) {
        ActorRef actor = terminated.getRef();
        ServiceKey key = actorKeys.get(actor);
        if (key != null) {
            Set<ActorRef<InvocationEvents.Request>> set =  actors.get(key);
            if (set != null) {
                set.remove(actor);
                registry.onTerminated(key.id(), actor);
            }
        }
        return Behaviors.same();
    }

    private Behavior<InvocationEvents> routes(Routes message) {
        ServiceKey key = ServiceKey.create(InvocationEvents.Request.class, message.getPath());
        Set<ActorRef<InvocationEvents.Request>> actors = this.actors.get(key);
        if (actors == null) {
            message.getSender().tell(new Response(context.getSelf(), Collections.emptySet()));
        } else {
            message.getSender().tell(new Response(context.getSelf(), actors));
        }
        return Behaviors.same();
    }

}