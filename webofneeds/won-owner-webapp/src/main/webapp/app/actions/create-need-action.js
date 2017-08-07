/**
 * Created by ksinger on 04.08.2017.
 */

import Immutable from 'immutable';

import {
    buildCreateMessage,
} from '../won-message-utils';

import {
    actionCreators,
    actionTypes,
} from './actions';

import {
   accountRegister,
} from './account-actions';

import {
    registerAccount,
    generateAccountCredentials,
    login,
} from '../won-utils';

import {
    delay,
} from '../utils';

export function needCreate(draft, nodeUri) {
    return (dispatch, getState) => {
        const { message, eventUri, needUri } = buildCreateMessage(draft, nodeUri);

        const state = getState();
        let email = state.getIn(['user', 'email']);
        let hasAccountPromise;

        if(state.getIn(['user', 'loggedIn'])){
            hasAccountPromise = Promise.resolve();
        } else {
            const {email, password, privateId} = generateAccountCredentials();
            hasAccountPromise = accountRegister(email, password)(dispatch)
                    .then(() =>
                        dispatch(actionCreators.router__stateGoCurrent({ privateId })) // add anonymous id to query-params
                    )
                    .then(() =>
                        // wait for the server to process the login and the reconnect to
                        // go through, before proceeding to need-creation.
                        delay(500)
                    )
                    .catch(err => {
                        //TODO user-visible error message / error recovery mechanisms
                        console.error(`Creating temporary account ${email} has failed due to `, err);
                    })
        }

        return hasAccountPromise
            .then(() => {
                dispatch({
                    type: actionTypes.needs.create,
                    payload: {eventUri, message, needUri}
                });
            })
            .then(() =>
                dispatch(actionCreators.router__stateGoResetParams('feed'))
            );
    }
}