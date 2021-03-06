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

package won.bot.framework.eventbot.action.impl.debugbot;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Model;
import won.bot.framework.eventbot.EventListenerContext;
import won.bot.framework.eventbot.action.BaseEventBotAction;
import won.bot.framework.eventbot.behaviour.ValidateConnectionDataBehaviour;
import won.bot.framework.eventbot.bus.EventBus;
import won.bot.framework.eventbot.event.BaseNeedAndConnectionSpecificEvent;
import won.bot.framework.eventbot.event.ConnectionSpecificEvent;
import won.bot.framework.eventbot.event.Event;
import won.bot.framework.eventbot.event.MessageEvent;
import won.bot.framework.eventbot.event.impl.command.close.CloseCommandEvent;
import won.bot.framework.eventbot.event.impl.command.connectionmessage.ConnectionMessageCommandEvent;
import won.bot.framework.eventbot.event.impl.command.deactivate.DeactivateNeedCommandEvent;
import won.bot.framework.eventbot.event.impl.debugbot.*;
import won.bot.framework.eventbot.event.impl.validate.ValidateConnectionCommandEvent;
import won.bot.framework.eventbot.event.impl.validate.ValidateConnectionCommandFailureEvent;
import won.bot.framework.eventbot.event.impl.validate.ValidateConnectionCommandSuccessEvent;
import won.bot.framework.eventbot.listener.EventListener;
import won.protocol.message.WonMessage;
import won.protocol.model.Connection;
import won.protocol.util.WonRdfUtils;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Listener that reacts to incoming messages, creating internal bot events for them
 */
public class DebugBotIncomingMessageToEventMappingAction extends BaseEventBotAction {

    Pattern PATTERN_USAGE = Pattern.compile("^usage|\\?|help|debug$", Pattern.CASE_INSENSITIVE);
    Pattern PATTERN_HINT = Pattern.compile("^hint$", Pattern.CASE_INSENSITIVE);
    Pattern PATTERN_CLOSE = Pattern.compile("^close$", Pattern.CASE_INSENSITIVE);
    Pattern PATTERN_CONNECT = Pattern.compile("^connect$", Pattern.CASE_INSENSITIVE);
    Pattern PATTERN_DEACTIVATE = Pattern.compile("^deactivate$", Pattern.CASE_INSENSITIVE);
    Pattern PATTERN_CHATTY_ON = Pattern.compile("^chatty\\s+on$", Pattern.CASE_INSENSITIVE);
    Pattern PATTERN_CHATTY_OFF = Pattern.compile("^chatty\\s+off$", Pattern.CASE_INSENSITIVE);
    Pattern PATTERN_SEND_N = Pattern.compile("^send ([1-9])$", Pattern.CASE_INSENSITIVE);
    Pattern PATTERN_VALIDATE = Pattern.compile("^validate$", Pattern.CASE_INSENSITIVE);


    public static final String[] USAGE_MESSAGES = {
            "You are connected to the debug bot. You can issue commands that will cause interactions with your need.",
            "Usage:",
            "    'hint':        create a new need and send hint to it",
            "    'connect':     create a new need and send connection request to it",
            "    'close':       close the current connection",
            "    'deactivate':  deactivate remote need of the current connection",
            "    'chatty on|off': (do not) send chat messages spontaneously every now and then (default: on)",
            "    'send N':      send N messages, one per second. N must be an integer between 1 and 9",
            "    'validate':    download the connection data and validate it",
            "    'usage':       display this message"
    };

    public static final String[] N_MESSAGES = {
            "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"
    };

    public static final String[] RANDOM_MESSAGES = {
            "Is there anything I can do for you?",
            "Did you read the news today?",
            "By the way, don't you just love the weather these days?",
            "Type 'usage' to see what I can do for you!",
            "I think I might see a movie tonight",
    };

    public static final String[] LAST_MESSAGES = {
            "?", "Are you still there?", "Gone?", "... cu later, I guess?", "Do you still require my services? You can use " +
            "the 'close' command, you know...", "Ping?"
    };

    public DebugBotIncomingMessageToEventMappingAction(EventListenerContext eventListenerContext) {
        super(eventListenerContext);
    }

    @Override
    protected void doRun(final Event event, EventListener executingListener) throws Exception {
        if (event instanceof BaseNeedAndConnectionSpecificEvent) {
            handleTextMessageEvent((ConnectionSpecificEvent) event);
        }
    }

    private void handleTextMessageEvent(final ConnectionSpecificEvent messageEvent) {
        if (messageEvent instanceof MessageEvent) {
            EventListenerContext ctx = getEventListenerContext();
            EventBus bus = ctx.getEventBus();

            Connection con = ((BaseNeedAndConnectionSpecificEvent) messageEvent).getCon();
            WonMessage msg = ((MessageEvent) messageEvent).getWonMessage();
            String message = extractTextMessageFromWonMessage(msg);

            try {
                if (PATTERN_USAGE.matcher(message).matches()) {
                    bus.publish(new UsageDebugCommandEvent(con));
                } else if (PATTERN_HINT.matcher(message).matches()) {
                    Model messageModel = WonRdfUtils.MessageUtils.textMessage("Ok, I'll create a new need and make it send a hint to you.");

                    bus.publish(new ConnectionMessageCommandEvent(con, messageModel));
                    bus.publish(new HintDebugCommandEvent(con));
                } else if (PATTERN_CONNECT.matcher(message).matches()) {
                    Model messageModel = WonRdfUtils.MessageUtils.textMessage("Ok, I'll create a new need and make it send a connect to you.");

                    bus.publish(new ConnectionMessageCommandEvent(con, messageModel));
                    bus.publish(new ConnectDebugCommandEvent(con));
                } else if (PATTERN_CLOSE.matcher(message).matches()) {
                    Model messageModel = WonRdfUtils.MessageUtils.textMessage("Ok, I'll close this connection");

                    bus.publish(new ConnectionMessageCommandEvent(con, messageModel));
                    bus.publish(new CloseCommandEvent(con));
                } else if (PATTERN_DEACTIVATE.matcher(message).matches()) {
                    Model messageModel = WonRdfUtils.MessageUtils.textMessage("Ok, I'll deactivate this need. This will close the connection we are currently talking on.");

                    bus.publish(new ConnectionMessageCommandEvent(con, messageModel));
                    bus.publish(new DeactivateNeedCommandEvent(con.getNeedURI()));
                } else if (PATTERN_CHATTY_ON.matcher(message).matches()) {
                    Model messageModel = WonRdfUtils.MessageUtils.textMessage("Ok, I'll send you messages spontaneously from time to time.");

                    bus.publish(new ConnectionMessageCommandEvent(con, messageModel));
                    bus.publish(new SetChattinessDebugCommandEvent(con, true));
                } else if (PATTERN_CHATTY_OFF.matcher(message).matches()) {
                    Model messageModel = WonRdfUtils.MessageUtils.textMessage("Ok, from now on I will be quiet and only respond to your messages.");

                    bus.publish(new ConnectionMessageCommandEvent(con, messageModel));
                    bus.publish(new SetChattinessDebugCommandEvent(con, false));
                } else if (PATTERN_SEND_N.matcher(message).matches()) {
                    Matcher m = PATTERN_SEND_N.matcher(message);
                    m.find();
                    String nStr = m.group(1);
                    int n = Integer.parseInt(nStr);

                    bus.publish(new SendNDebugCommandEvent(con, n));
                }else if (PATTERN_VALIDATE.matcher(message).matches()) {
                    //acknowledge the command
                    Model messageModel = WonRdfUtils.MessageUtils.textMessage("Ok, I'm going to validate the data in our connection. This may take a while.");
                    bus.publish(new ConnectionMessageCommandEvent(con, messageModel));
                    //start validation behaviour
                    ValidateConnectionCommandEvent command = new ValidateConnectionCommandEvent(con.getNeedURI(), con.getConnectionURI());
                    ValidateConnectionDataBehaviour validateConnectionDataBehaviour = new ValidateConnectionDataBehaviour(ctx,command, Duration.ofSeconds(60));
                    validateConnectionDataBehaviour.onResult(new BaseEventBotAction(ctx) {
                        @Override
                        protected void doRun(Event event, EventListener executingListener) throws Exception {
                            if (event instanceof ValidateConnectionCommandSuccessEvent) {
                                ValidateConnectionCommandSuccessEvent successEvent = (ValidateConnectionCommandSuccessEvent) event;
                                Model messageModel = WonRdfUtils.MessageUtils
                                        .textMessage(successEvent.getMessage());
                                bus.publish(new ConnectionMessageCommandEvent(con, messageModel));
                                if (!successEvent.isValid()) {
                                    String message = successEvent.getMessage();
                                    if (message == null || message.trim().length() == 0) {
                                        message = "[no message available]";
                                    }
                                    messageModel = WonRdfUtils.MessageUtils
                                            .textMessage("Problem report: " + message);
                                    bus.publish(new ConnectionMessageCommandEvent(con, messageModel));
                                }
                            } else if (event instanceof ValidateConnectionCommandFailureEvent){
                                String message = ((ValidateConnectionCommandFailureEvent)event).getMessage();
                                if (message == null || message.trim().length() == 0) {
                                    message = "[no message available]";
                                }
                                Model messageModel = WonRdfUtils.MessageUtils
                                        .textMessage("Could not validate connection data. Problem: " + message);
                                bus.publish(new ConnectionMessageCommandEvent(con, messageModel));
                            }
                        }
                    });
                    validateConnectionDataBehaviour.activate();
                } else {
                    //default: answer with eliza.
                    bus.publish(new MessageToElizaEvent(con, message));
                }
            } catch (Exception e) {
                //error: send an error message
                Model messageModel = WonRdfUtils.MessageUtils.textMessage("Did not understand your command '" + message + "': " + e.getClass().getSimpleName() + ":" + e.getMessage());
                bus.publish(new ConnectionMessageCommandEvent(con, messageModel));
            }
        }
    }

    private String extractTextMessageFromWonMessage(WonMessage wonMessage) {
        if (wonMessage == null) return null;
        String message = WonRdfUtils.MessageUtils.getTextMessage(wonMessage);
        return StringUtils.trim(message);
    }
}
