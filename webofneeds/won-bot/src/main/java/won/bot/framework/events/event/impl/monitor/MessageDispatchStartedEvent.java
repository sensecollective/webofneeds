package won.bot.framework.events.event.impl.monitor;

import won.protocol.message.WonMessage;

/**
 * User: ypanchenko
 * Date: 04.03.2016
 */
public class MessageDispatchStartedEvent extends MessageSpecificEvent
{
  public MessageDispatchStartedEvent(final WonMessage message) {
    super(message);
  }
}
