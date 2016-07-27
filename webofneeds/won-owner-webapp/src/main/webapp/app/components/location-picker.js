/**
 * Created by ksinger on 15.07.2016.
 */

import won from '../won-es6';
import angular from 'angular';
import Immutable from 'immutable'; // also exports itself as (window).L
import 'leaflet';
import 'ng-redux';
//import { labels } from '../won-label-utils';
import {
    attach,
    searchNominatim,
    reverseSearchNominatim,
} from '../utils.js';
import { actionCreators }  from '../actions/actions';
import { } from '../selectors';
import { doneTypingBufferNg } from '../cstm-ng-utils'

const serviceDependencies = ['$scope', '$ngRedux', '$element'];
function genComponentConf() {
    //TODO input as text-input or contenteditable? need to overl
    let template = `


        <input type="text" class="lp__searchbox" placeholder="Search for location"/>
        <ol>
            <li ng-repeat="result in self.searchResults">
                <a href="" ng-click="self.selectedLocation(result)">
                    {{ result.name }}
                </a>
            </li>
        </ol>
        <div class="lp__mapmount" id="lp__mapmount" style="height:500px"></div>
        <!--<img class="lp__mapmount" src="images/some_map_screenshot.png"alt=""/>-->
            `;

    class Controller {
        constructor() {
            attach(this, serviceDependencies, arguments);

            this.initMap();

            window.lp4dbg = this;
            const selectFromState = (state)=>{
                return {
                };
            }

            doneTypingBufferNg(
                e => this.doneTyping(e),
                this.textfieldNg(), 1000
            )

            const disconnect = this.$ngRedux.connect(selectFromState, actionCreators)(this);
            this.$scope.$on('$destroy', disconnect);

        }
        initMap() {
            // Leaflet + JS-Bundling fix:
            L.Icon.Default.imagePath = 'images/map-images/';
            //TODO replace with own icons

            const secureOsmSource = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png' // secure osm.org
            const secureOsm = L.tileLayer(secureOsmSource, {
                attribution: '&copy; <a href="http://osm.org/copyright">OpenStreetMap</a> contributors'
            });

            const transportSource = 'http://{s}.tile2.opencyclemap.org/transport/{z}/{x}/{y}.png';
            const transport = L.tileLayer(transportSource, {
                attribution: 'Maps &copy; <a href="http://www.thunderforest.com">Thunderforest</a>, Data &copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>',
            });

            this.map = L.map(this.mapMount(),{
                center: [37.44, -42.89], //centered on north-west africa
                zoom: 1, //world-map
                layers: [secureOsm], //initially visible layers

            }); //.setView([51.505, -0.09], 13);

            //this.map.fitWorld() // shows every continent twice :|
            this.map.fitBounds([[-80, -190],[80, 190]]); // fitWorld without repetition

            const baseMaps = {
                "Detailed default map": secureOsm,
                "Transport (Insecurely loaded!)": transport,
            };

            L.control.layers(baseMaps).addTo(this.map);

            this.map.on('click', e => onMapClick(e, this));

            // Force it to adapt to actual size
            // for some reason this doesn't happen by default
            // when the map is within a tag.
            // this.map.invalidateSize();
            // ^ doesn't work (needs to be done manually atm);

        }
        placeMarkers(locations) {
            if(this.markers) {
                //remove previously placed markers
                for(let m of this.markers) {
                    this.map.removeLayer(m);
                }
            }

            this.markers = locations.map(location =>
                L.marker([location.lat, location.lon])
                .bindPopup(location.name)
            );

            for(let m of this.markers) {
                this.map.addLayer(m);
            }
        }
        selectedLocation(location) {
            this.searchResults = undefined; // picked one, can hide the rest if they were there
            console.log('selected location: ', location);

            this.placeMarkers([location]);
            this.map.fitBounds(location.bounds, { animate: true });
            this.markers[0].openPopup();
        }
        doneTyping() {
            console.log('starting type-ahead search for: ' + this.textfield().value);
            //buffer for 1s before starting the search
            searchNominatim(this.textfield().value)
            .then( searchResults => {
                console.log('location search results: ', searchResults);
                this.$scope.$apply(() => {
                    this.searchResults = scrubSearchResults(searchResults);
                    this.placeMarkers(searchResults);
                })
            })

        }

            }

        }
            }
        }

        elementNg(selector) {
            if(!this._elementsNg) {
                this._elementsNg = {};
            }
            if(!this._elementsNg[selector]) {
                this._elementsNg[selector] = this.$element.find(selector);
            }
            return this._elementsNg[selector];
        }
        element(selector) {
            return this.elementNg(selector)[0];
        }

        textfieldNg() { return this.elementNg('.lp__searchbox'); }

        textfield() { return this.element('.lp__searchbox'); }

        mapMountNg() { return this.elementNg('.lp__mapmount'); }

        mapMount() { return this.element('.lp__mapmount'); }
    }
    Controller.$inject = serviceDependencies;


    return {
        restrict: 'E',
        controller: Controller,
        controllerAs: 'self',
        bindToController: true, //scope-bindings -> ctrl
        scope: {
        },
        template: template
    }
}


function scrubSearchResults(searchResults) {

    return Immutable.fromJS(
            searchResults.map(nominatim2wonLocation)
        )
        /*
         * filter "duplicate" results (e.g. "Wien"
         *  -> 1x waterway, 1x boundary, 1x place)
         */
        .groupBy(r => r.get('name'))
        .map(sameNamedResults => sameNamedResults.first())
        .toList()
        .toJS()
}

/**
 * drop info not stored in rdf, thus info that we
 * couldn't restore for previously used locations
 */
function nominatim2wonLocation(searchResult) {
    const b = searchResult.boundingbox;
    return {
        name: searchResult.display_name,
        lon: searchResult.lon,
        lat: searchResult.lat,
        //importance: searchResult.importance,
        bounds: [
            [ b[0], b[2] ], //north-western point
            [ b[1], b[3] ] //south-eastern point
        ],
        //boundingbox: searchResult.boundingbox, // TODO use this to set proper zoom
    }
}

function onMapClick(e, ctrl) {
    //`this` is the mapcontainer here as leaflet
    // apparently binds itself to the function.
    // This code was moved out of the controller
    // here to avoid confusion resulting from
    // this binding.
    console.log('clicked map ', e);
    reverseSearchNominatim(
        e.latlng.lat,
        e.latlng.lng,
        ctrl.map.getZoom()// - 1
    ).then(searchResult => {
        console.log('nearest address: ', searchResult);
        const location = nominatim2wonLocation(searchResult);
        ctrl.selectedLocation(location);
    });
}

export default angular.module('won.owner.components.locationPicker', [
    ])
    .directive('wonLocationPicker', genComponentConf)
    .name;


window.searchNominatim4dbg = searchNominatim;