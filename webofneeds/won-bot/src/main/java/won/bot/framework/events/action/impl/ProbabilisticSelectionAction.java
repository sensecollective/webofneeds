/*
 * Copyright 2012  Research Studios Austria Forschungsges.m.b.H.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package won.bot.framework.events.action.impl;

import won.bot.framework.events.EventListenerContext;
import won.bot.framework.events.action.BaseEventBotAction;
import won.bot.framework.events.action.EventBotAction;
import won.bot.framework.events.event.Event;

import java.util.Random;

/**
 * Action that delegates to delegateA with probabilityA or to delegateB with probability 1-probabilityA.
 */
public class ProbabilisticSelectionAction extends BaseEventBotAction
{
  private double probabilityA;
  private EventBotAction delegateA;
  private EventBotAction delegateB;
  private long salt = 0;
  private Random random;

  public ProbabilisticSelectionAction(final EventListenerContext eventListenerContext, final double probabilityA,
    final long salt, final EventBotAction delegateA, final EventBotAction delegateB) {
    super(eventListenerContext);
    this.probabilityA = probabilityA;
    this.delegateA = delegateA;
    this.delegateB = delegateB;
    this.salt = 0;
    this.random = new Random(System.currentTimeMillis()+ salt);
    assert probabilityA >= 0 && probabilityA <= 1 : "probability must be in [0,1]";
    assert delegateA != null : "delegateA must not be null";
    assert delegateB != null : "delegateB must not be null";
  }

  @Override
  protected void doRun(final Event event) throws Exception {
    double outcome = random.nextDouble();
    if (outcome <= probabilityA){
      getEventListenerContext().getExecutor().execute(delegateA.getActionTask(event));
    } else {
      getEventListenerContext().getExecutor().execute(delegateB.getActionTask(event));
    }
  }
}
