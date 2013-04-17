/*
 * Copyright 2012  Research Studios Austria Forschungsges.m.b.H.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package won.protocol.model;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;

/**
 * User: fkleedorfer
 * Date: 20.11.12
 */
public class WON {
    //TODO change URI style to '#' style for the ontology
    public static final String BASE_URI = "http://purl.org/webofneeds/model#";

    private static Model m = ModelFactory.createDefaultModel();

    //NEW
    public static final Property NEED = m.createProperty(BASE_URI + "Need");
    public static final Property NEED_CREATION_DATE = m.createProperty(BASE_URI + "needCreationDate");
    public static final Property NEED_PROTOCOL_ENDPOINT = m.createProperty(BASE_URI + "needProtocolEndpoint");
    public static final Property MATCHER_PROTOCOL_ENDPOINT = m.createProperty(BASE_URI + "matcherProtocolEndpoint");
    public static final Property OWNER_PROTOCOL_ENDPOINT = m.createProperty(BASE_URI + "ownerProtocolEndpoint");

    public static final Property IS_IN_STATE = m.createProperty(BASE_URI + "isInState");
    public static final Property NEED_STATE = m.createProperty(BASE_URI + "NeedState");

    public static final Property HAS_BASIC_NEED_TYPE = m.createProperty(BASE_URI + "hasBasicNeedType");
    public static final Property BASIC_NEED_TYPE = m.createProperty(BASE_URI + "BasicNeedType");

    public static final Property HAS_CONTENT = m.createProperty(BASE_URI + "hasContent");
    public static final Property NEED_CONTENT = m.createProperty(BASE_URI + "NeedContent");
    public static final Property TEXT_DESCRIPTION = m.createProperty(BASE_URI + "textDescription");
    public static final Property CONTENT_DESCRIPTION = m.createProperty(BASE_URI + "contentDescription");
    public static final Property HEIGHT = m.createProperty(BASE_URI + "height");
    public static final Property DEPTH = m.createProperty(BASE_URI + "depth");
    public static final Property WIDTH = m.createProperty(BASE_URI + "width");
    public static final Property WEIGHT = m.createProperty(BASE_URI + "weight");
    public static final Property QUANTITATIVE_PROPERTY = m.createProperty(BASE_URI + "quantitativeProperty");

    public static final Property HAS_OWNER = m.createProperty(BASE_URI + "hasOwner");
    public static final Property OWNER = m.createProperty(BASE_URI + "Owner");
    public static final Property ANONYMIZED_OWNER = m.createProperty(BASE_URI + "AnonymizedOwner");

    public static final Property HAS_CONNECTIONS = m.createProperty(BASE_URI + "hasConnections");
    public static final Property CONNECTION_CONTAINER = m.createProperty(BASE_URI + "ConnectionContainer");
    public static final Property CONNECTION = m.createProperty(BASE_URI + "Connection");
    public static final Property HAS_REMOTE_CONNECTION = m.createProperty(BASE_URI + "remoteConnection");

    public static final Property HAS_EVENT_CONTAINER = m.createProperty(BASE_URI + "hasEventContainer");
    public static final Property EVENT_CONTAINER = m.createProperty(BASE_URI + "EventContainer");
    public static final Property EVENT = m.createProperty(BASE_URI + "Event");
    public static final Property OCCURED_AT = m.createProperty(BASE_URI + "occuredAt");
    public static final Property HAS_ORIGINATOR = m.createProperty(BASE_URI + "hasOriginator");

    public static final Property NEED_MODALITY = m.createProperty(BASE_URI + "NeedModality");

    public static final Property HAS_PRICE_SPECIFICATION = m.createProperty(BASE_URI + "hasPriceSpecification");
    public static final Property PRICE_SPECIFICATION = m.createProperty(BASE_URI + "PriceSpecification");
    public static final Property HAS_LOWER_PRICE_LIMIT = m.createProperty(BASE_URI + "hasLowerPriceLimit");
    public static final Property HAS_UPPER_PRICE_LIMIT = m.createProperty(BASE_URI + "hasUpperPriceLimit");
    public static final Property HAS_CURRENCY = m.createProperty(BASE_URI + "hasCurrency");

    public static final Property AVAILABLE_LOCATION = m.createProperty(BASE_URI + "availableLocation");
    public static final Property LOCATION = m.createProperty(BASE_URI + "Location");
    public static final Property IS_CONCEALED = m.createProperty(BASE_URI + "isConcealed");
    public static final Property REGION = m.createProperty(BASE_URI + "Region");
    public static final Property HAS_ISO_CODE = m.createProperty(BASE_URI + "hasISOCode");

    public static final Property AVAILABLE_AT_TIME = m.createProperty(BASE_URI + "availableAtTime");
    public static final Property TIME_CONSTRAINT = m.createProperty(BASE_URI + "TimeConstraint");
    public static final Property START_TIME = m.createProperty(BASE_URI + "startTime");
    public static final Property END_TIME = m.createProperty(BASE_URI + "endTime");
    public static final Property RECUR_IN = m.createProperty(BASE_URI + "recurIn");
    public static final Property RECUR_TIMES = m.createProperty(BASE_URI + "recurTimes");
    public static final Property RECUR_INFINITE_TIMES = m.createProperty(BASE_URI + "recurInfiniteTimes");

    //OLD
    @Deprecated
    public static final Property NEED_DESCRIPTION = m.createProperty(BASE_URI + "NeedDescription");
    @Deprecated
    public static final Property RESOURCE_URI = m.createProperty(BASE_URI + "resourceUri");
    @Deprecated
    public static final Property REMOTE_NEED = m.createProperty(BASE_URI + "remoteNeed");
    @Deprecated
    public static final Property BELONGS_TO_NEED = m.createProperty(BASE_URI + "belongsToNeed");

    /**
     * Returns the URI for this schema.
     *
     * @return the URI for this schema
     */
    public static String getURI() {
        return BASE_URI;
    }

}
