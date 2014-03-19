package won.bot.framework.events.listener.baStateBots.baCCMessagingBots.coordinationMessageAsUriBots;

import won.bot.framework.events.listener.baStateBots.BATestBotScript;
import won.bot.framework.events.listener.baStateBots.BATestScriptAction;
import won.bot.framework.events.listener.baStateBots.WON_BA;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * User: Danijel
 * Date: 19.3.14.
 */
public class BACCStateCompleteWithFailuresUriBot extends BATestBotScript {

    @Override
    protected List<BATestScriptAction> setupActions() {
        List<BATestScriptAction> actions = new ArrayList();
        actions.add(new BATestScriptAction(false, URI.create(WON_BA.MESSAGE_NOTCOMPLETED.getURI()), URI.create(WON_BA.STATE_ACTIVE.getURI())));
        actions.add(new BATestScriptAction(true, URI.create(WON_BA.MESSAGE_COMPENSATED.getURI()), URI.create(WON_BA.STATE_ACTIVE.getURI())));
        actions.add(new BATestScriptAction(false, URI.create(WON_BA.MESSAGE_COMPLETE.getURI()), URI.create(WON_BA.STATE_ACTIVE.getURI())));
        actions.add(new BATestScriptAction(false, URI.create(WON_BA.MESSAGE_COMPLETE.getURI()), URI.create(WON_BA.STATE_ACTIVE.getURI())));
        actions.add(new BATestScriptAction(true, URI.create(WON_BA.MESSAGE_COMPLETED.getURI()), URI.create(WON_BA.STATE_COMPLETING.getURI())));
        actions.add(new BATestScriptAction(false, URI.create(WON_BA.MESSAGE_CLOSE.getURI()), URI.create(WON_BA.STATE_COMPLETED.getURI())));
        actions.add(new BATestScriptAction(true, URI.create(WON_BA.MESSAGE_CANNOTCOMPLETE.getURI()), URI.create(WON_BA.STATE_CLOSING.getURI())));
        actions.add(new BATestScriptAction(false, URI.create(WON_BA.MESSAGE_COMPLETE.getURI()), URI.create(WON_BA.STATE_CLOSING.getURI())));
        actions.add(new BATestScriptAction(true, URI.create(WON_BA.MESSAGE_CLOSED.getURI()), URI.create(WON_BA.STATE_CLOSING.getURI())));
        return actions;
    }
}