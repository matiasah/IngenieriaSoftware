// Copyright 2017 The Nomulus Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

goog.provide('registry.Console');

goog.require('goog.Disposable');
goog.require('goog.History');
goog.require('goog.dom');
goog.require('goog.events');
goog.require('goog.events.EventType');
goog.require('goog.events.KeyCodes');
goog.require('goog.history.EventType');
goog.require('registry.util');

goog.forwardDeclare('goog.events.KeyEvent');
goog.forwardDeclare('registry.Session');



/**
 * Abstract console for both admin and registrar console UIs.
 * @param {?registry.Session} session server request session.
 * @constructor
 * @extends {goog.Disposable}
 */
registry.Console = function(session) {
  registry.Console.base(this, 'constructor');

  /**
   * @type {!goog.History}
   * @protected
   */
  this.history = new goog.History();
  goog.events.listen(
      this.history,
      goog.history.EventType.NAVIGATE,
      goog.bind(this.handleHashChange, this));

  /**
   * @type {?registry.Session} The server session.
   */
  this.session = session;

  this.bindToDom();
};
goog.inherits(registry.Console, goog.Disposable);


/**
 * Helper to setup permanent page elements.
 */
registry.Console.prototype.bindToDom = function() {
  registry.util.unbutter();
  goog.events.listen(goog.dom.getRequiredElement('kd-searchbutton'),
                     goog.events.EventType.CLICK,
                     goog.bind(this.onSearch_, this));
  goog.events.listen(goog.dom.getRequiredElement('kd-searchfield'),
                     goog.events.EventType.KEYUP,
                     goog.bind(this.onSearchFieldKeyUp_, this));
  goog.events.listen(
      goog.dom.getElementByClass(goog.getCssName('kd-butterbar-dismiss')),
      goog.events.EventType.CLICK,
      registry.util.unbutter);
};


/**
 * Subclasses should override to visit the hash token given by
 * {@code goog.History.getToken()}.
 */
registry.Console.prototype.handleHashChange = goog.abstractMethod;


/**
 * @param {string} resourcePath Resource description path.
 */
registry.Console.prototype.view = function(resourcePath) {
  this.history.setToken(resourcePath);
};


/**
 * Handler for search bar.
 * @private
 */
registry.Console.prototype.onSearch_ = function() {
  var qElt = goog.dom.getRequiredElement('kd-searchfield');
  if (qElt.getAttribute('disabled')) {
    return;
  }
  var query = qElt.value;
  if (query == '') {
    return;
  }
  // Filtering this value change event.
  qElt.setAttribute('disabled', true);
  qElt.value = '';
  this.view(query);
  qElt.removeAttribute('disabled');
};


/**
 * Handler for key press in the search input field.
 * @param {!goog.events.KeyEvent} e Key event to handle.
 * @return {boolean} Whether the event should be continued or cancelled.
 * @private
 */
registry.Console.prototype.onSearchFieldKeyUp_ = function(e) {
  if (e.keyCode == goog.events.KeyCodes.ENTER) {
    this.onSearch_();
    return false;
  }
  return true;
};
