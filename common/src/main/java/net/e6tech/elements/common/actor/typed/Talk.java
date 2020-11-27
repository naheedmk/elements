/*
 * Copyright 2015-2020 Futeh Kao
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

package net.e6tech.elements.common.actor.typed;

import akka.actor.PoisonPill;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.AskPattern;
import net.e6tech.elements.common.util.SystemException;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

@SuppressWarnings({"squid:S1172", "squid:S00119"})
public class Talk<T> {
    private long timeout;
    private Guardian guardian;
    private ActorRef<T> recipient;

    public Talk(Guardian guardian, ActorRef<T> recipient) {
        this.guardian = guardian;
        this.timeout = guardian.getTimeout();
        this.recipient = recipient;
    }

    public Talk<T> timeout(long timout) {
        this.timeout = timout;
        return this;
    }

    public <Res> CompletionStage<Res> ask(Function<ActorRef<Res>, T> msgFactory) {
        return AskPattern.ask(recipient, msgFactory::apply,
                java.time.Duration.ofMillis(timeout), guardian.getScheduler());
    }

    public <Res> Res askAndWait(Class<Res> retClass, Function<ActorRef<Res>, T> msgFactory) {
        return askAndWait(msgFactory);
    }

    public <Res> Res askAndWait(Function<ActorRef<Res>, T> msgFactory) {
        CompletionStage<Res> stage = AskPattern.ask(recipient, msgFactory::apply,
                java.time.Duration.ofMillis(timeout), guardian.getScheduler());
        try {
            return stage.toCompletableFuture().get();
        } catch (Exception e) {
            throw new SystemException(e);
        }
    }

    public Talk<T> tell(T msg) {
        recipient.tell(msg);
        return this;
    }

    @SuppressWarnings("unchecked")
    public void stop() {
        AskPattern.ask((ActorRef)recipient, ref -> PoisonPill.getInstance(),
                java.time.Duration.ofSeconds(10), guardian.getScheduler());
    }

    public CompletionStage<Void> async(Runnable runnable) {
        return guardian.async(runnable, timeout);
    }

    public <R> CompletionStage<R> async(Callable<R> callable) {
        return guardian.async(callable, timeout);
    }
}
