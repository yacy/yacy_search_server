/*    
* Copyright (C) 2019 by luccioman; https://github.com/luccioman
*         
* This file is part of YaCy.
* 
* YaCy is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
* 
* YaCy is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
* 
* You should have received a copy of the GNU General Public License
* along with YaCy.  If not, see <http://www.gnu.org/licenses/>.
*/

/* Functions dedicated to control playing of YaCy audio search results */

/**
 * Show elements that are only useful when JavaScript is enabled
 */
function showJSAudioControls() {
	var audioElems = document.getElementsByTagName("audio");
	var audioControls = document.getElementById("audioControls");
    if(audioElems != null &&  audioElems.length > 0 && audioControls != null && audioControls.className.indexOf("hidden") >= 0) {
    	audioControls.className = audioControls.className.replace("hidden", "");
    }
    
    var expandAudioButtons = document.getElementsByClassName("expandAudiosBtn");
    if(expandAudioButtons != null) {
    	for(var i = 0; i < expandAudioButtons.length; i++) {
    		expandAudioButtons[i].className = expandAudioButtons[i].className.replace("hidden", "");
    	}
    }
}

/**
 * Handle embedded audio result load error.
 * 
 * @param event
 *            {ErrorEvent} the error event triggered
 */
function handleAudioLoadError(event) {
	if (event != null && event.target != null) {
		/* Fill the title attribute to provide some feedback about the error without need for looking at the console */
		var titleAddition;
		if (event.target.error != null && event.target.error.message) {
			titleAddition = " - Cannot play ("
					+ event.target.error.message + ")";
		} else {
			titleAddition = " - Cannot play";
		}
		if(event.target.title == null || event.target.title.indexOf(titleAddition) < 0) {
			event.target.title += titleAddition;
		}
		
		/* Apply CSS class marking error for visual feedback*/
		event.target.className = "audioError";
		
		/* Start playing the next audio result when "Play all" is running */
		var playerElem = document.getElementById("audioControls");
		var currentPlayAllTrack = playerElem != null ? parseInt(playerElem
				.getAttribute("data-current-track")) : -1; 
		if(!isNaN(currentPlayAllTrack) && currentPlayAllTrack >= 0) {
			/* Currently playing all audio results */
			playAllNext(playerElem);
		}
	}
}

/**
 * Handle embedded audio result 'playing' event : pauses any other currently
 * playing audio.
 * 
 * @param event
 *            {Event} a 'playing' event
 */
function handleAudioPlaying(event) {
	if (event != null && event.target != null) {
		var audioElems = document.getElementsByTagName("audio");
		var playerElem = document.getElementById("audioControls");
		var currentPlayAllTrack = playerElem != null ? parseInt(playerElem
				.getAttribute("data-current-track")) : -1;
		if (audioElems != null) {
			for (var i = 0; i < audioElems.length; i++) {
				var audioElem = audioElems[i];

				if (audioElem == event.target) {
					if (!isNaN(currentPlayAllTrack) && currentPlayAllTrack >= 0) {
						if(currentPlayAllTrack == i) {
							/* Starting or resuming play of the currently selected "Play all" track */
							updatePlayAllButton(true);
						} else {
							/* Playing here a single result while "Play all" was running on a different track */
						
							/* "Play all" is interrupted */
							updatePlayAllButton(false);

							/* Update the current play all track index */
							playerElem.setAttribute("data-current-track", -1);
						}
					}
				} else if (audioElem.pause && !audioElem.paused) {
					audioElem.pause();
				}
			}
		}
	}
}

/**
 * Handle embedded audio result 'pause' event.
 * 
 * @param event
 *            {Event} a 'pause' event
 */
function handleAudioPause(event) {
	if(event != null && event.target != null && event.target.error == null) {
		var playerElem = document.getElementById("audioControls");
		var currentPlayAllTrack = playerElem != null ? parseInt(playerElem
				.getAttribute("data-current-track")) : -1; 
		if(!isNaN(currentPlayAllTrack) && currentPlayAllTrack >= 0) {
			/* Currently playing all audio results */
			var audioElems = document.getElementsByTagName("audio");
			if(audioElems != null) {
				for(var i = 0; i < audioElems.length; i++) {
					if(audioElems[i] == event.target && currentPlayAllTrack == i) {
						/* Update the "Play all" button icon to reflect the new status.
						 * Do not update when pausing another track that may be already paused. */
						updatePlayAllButton(false);		
					}
				}
			}
		}
	}
}

/**
 * Handle embedded audio result 'ended' event : start playing the next audio
 * result when the "Play all" button is pressed.
 * 
 * @param event
 *            {Event} a 'playing' event
 */
function handleAudioEnded(event) {
	if (event != null && event.target != null) {
		/* Start playing the next audio result when "Play all" is pressed */
		var playerElem = document.getElementById("audioControls");
		var currentPlayAllTrack = playerElem != null ? parseInt(playerElem
				.getAttribute("data-current-track")) : -1; 
		if(!isNaN(currentPlayAllTrack) && currentPlayAllTrack >= 0) {
			/* Currently playing all audio results */
			playAllNext(playerElem);
		}
	}
}

/**
 * Start playing the next audio result when one is available and update the
 * "Play all" button.
 * 
 * @param playerElem
 *            the player container element
 */
function playAllNext(playerElem) {
	if (playerElem == null) {
		return;
	}
	var audioElems = document.getElementsByTagName("audio");
	if (audioElems == null) {
		/* No more audio elements ? */
		playerElem.setAttribute("data-current-track", -1);
		updatePlayAllButton(false);
	} else {
		var currentTrack = parseInt(playerElem.getAttribute("data-current-track"));
		var nextTrack;
		if (isNaN(currentTrack) || currentTrack < 0
				|| currentTrack >= audioElems.length) {
			nextTrack = 0;
		} else {
			nextTrack = currentTrack + 1;
		}
		if (nextTrack < audioElems.length) {
			while (nextTrack < audioElems.length) {
				var audioElem = audioElems[nextTrack];
				if (audioElem != null && audioElem.error == null
						&& audioElem.play && audioElem.className.indexOf("hidden") < 0) {
					if(audioElem.currentTime > 0) {
						audioElem.currentTime = 0;
					}
					audioElem.play();
					updatePlayAllButton(true);
					playerElem.setAttribute("data-current-track", nextTrack);
					break;
				}
				/* Go to the next element when not playable */
				nextTrack++;
			}
			if(nextTrack >= audioElems.length) {
				/* No other result to play */
				if (currentTrack >= 0 && currentTrack < audioElems.length
						&& !audioElems[currentTrack].paused
						&& audioElems[currentTrack].pause) {
					audioElems[currentTrack].pause();
				}
				playerElem.setAttribute("data-current-track", -1);
				updatePlayAllButton(false);
			}
		} else {
			/* No other result to play */
			if (currentTrack >= 0 && currentTrack < audioElems.length
					&& !audioElems[currentTrack].paused
					&& audioElems[currentTrack].pause) {
				audioElems[currentTrack].pause();
			}
			playerElem.setAttribute("data-current-track", -1);
			updatePlayAllButton(false);
		}
	}
}

/**
 * Update the "Play all" button
 * @param playing when true the new status becomes playing all, otherwise it becomes paused
 */
function updatePlayAllButton(playing) {
	var playAllIcon = document.getElementById("playAllIcon");
	if (playAllIcon != null) {
		if(playing) {
			if(playAllIcon.className != "glyphicon glyphicon-pause") {
				playAllIcon.className = "glyphicon glyphicon-pause";
			}
		} else if(playAllIcon.className != "glyphicon glyphicon-play") {
			playAllIcon.className = "glyphicon glyphicon-play";
		}
	}
	var playAllBtn = document.getElementById("playAllBtn");
	if(playAllBtn != null) {
		if(playing) {
			var pauseTitle = playAllBtn.getAttribute("data-pause-title");
			if(pauseTitle != null && playAllBtn.title != pauseTitle) {
				playAllBtn.title = pauseTitle;
			}
		} else {
			var playAllTitle = playAllBtn.getAttribute("data-playAll-title");
			if(playAllTitle != null && playAllBtn.title != playAllTitle) {
				playAllBtn.title = playAllTitle;
			}
		}
	}
	
}

/**
 * Handle a click on the "Play all" button.
 */
function handlePlayAllBtnClick() {
	var audioElems = document.getElementsByTagName("audio");
	var playerElem = document.getElementById("audioControls");
	var playAllIcon = document.getElementById("playAllIcon");
	if (playerElem != null && audioElems != null && playAllIcon != null) {
		if (playAllIcon.className == "glyphicon glyphicon-play" && audioElems.length > 0) {
			var currentTrack = parseInt(playerElem
					.getAttribute("data-current-track"));
			if (isNaN(currentTrack) || currentTrack < 0
					|| currentTrack >= audioElems.length) {
				/* Start a new play of all audio results */
				currentTrack = 0;
				/* Reset all times */
				for (var j = 0; j < audioElems.length; j++) {
					var elem = audioElems[j];
					if (elem.paused && elem.currentTime > 0) {
						elem.currentTime = 0;
					}
				}
			}
			while (currentTrack < audioElems.length) {
				var currentAudioElem = audioElems[currentTrack];
				if (currentAudioElem != null && currentAudioElem.error == null
						&& currentAudioElem.play && currentAudioElem.className.indexOf("hidden") < 0) {
					currentAudioElem.play();
					updatePlayAllButton(true);
					break;
				}
				/* Go to the next element when not playable */
				currentTrack++;
			}
			playerElem.setAttribute("data-current-track", currentTrack);
		} else {
			/* Pause any running track */
			for (var i = 0; i < audioElems.length; i++) {
				var audioElem = audioElems[i];
				if (audioElem.pause && !audioElem.paused) {
					audioElem.pause();
				}
			}
		}
	}
}

/**
 * Handle a click on the "Stop all" button.
 */
function handleStopAllBtnClick() {
	/* Stop all audio elements */
	var audioElems = document.getElementsByTagName("audio");
	if (audioElems != null) {
		for (var i = 0; i < audioElems.length; i++) {
			var audioElem = audioElems[i];
			if (audioElem.pause && !audioElem.paused) {
				audioElem.pause();
				if(audioElem.currentTime > 0) {
					audioElem.currentTime = 0;	
				}
			} else if(audioElem.paused && audioElem.currentTime > 0) {
				audioElem.currentTime = 0;
			}
		}
	}
	
	updatePlayAllButton(false);

	/* Update the current track index */
	var playerElem = document.getElementById("audioControls");
	if (playerElem != null) {
		playerElem.setAttribute("data-current-track", -1);
	}
}

/**
 * Toggle visibility on a list of audio elements beyond the initial limit of elements to display.
 * @param {HTMLButtonElement} button the button used to expand the audio elements
 * @param {String} expandableAudiosId the id of the container of audio elements which visibility has to be toggled
 * @param {String} hiddenCountId the id of the element containing the number of hidden elements when expandable audios are collapsed
 * @param {String} evenMoreCountId the id of the eventual element containing the number of hidden elements remaining when audios are expanded
 */
function toggleExpandableAudios(button, expandableAudiosId, hiddenCountId, evenMoreCountId) {
	var expandableAudiosContainer = document.getElementById(expandableAudiosId);
	var hiddenCountElem = document.getElementById(hiddenCountId);
	var evenMoreElem = document.getElementById(evenMoreCountId);
	if(button != null && expandableAudiosContainer != null) {
		var childrenAudioElems = expandableAudiosContainer.getElementsByTagName("audio");
		var playerElem = document.getElementById("audioControls");
		var playAllIcon = document.getElementById("playAllIcon");
		var currentPlayAllTrack = playerElem != null ? parseInt(playerElem
				.getAttribute("data-current-track")) : -1;
		if(button.getAttribute("aria-expanded") == "true") {
			var currentPlayAllAudioElem = null;
			if(!isNaN(currentPlayAllTrack) && currentPlayAllTrack >= 0) {
				/* Currently playing all audio results */
				var audioElems = document.getElementsByTagName("audio");
				if(audioElems != null && audioElems.length > currentPlayAllTrack) {
					currentPlayAllAudioElem = audioElems[currentPlayAllTrack];
				}
			}
			/* Additionnaly we modify the aria-expanded state for improved accessiblity */
			button.setAttribute("aria-expanded", "false");
			button.title = button.getAttribute("data-title-collapsed");
			expandableAudiosContainer.className += " hidden";
			if(hiddenCountElem != null) {
				hiddenCountElem.className = hiddenCountElem.className.replace("hidden", "");
			}
			if(evenMoreElem != null) {
				evenMoreElem.className += " hidden";
			}
			var hidingPlayAll = false;
			for(var i = 0; i < childrenAudioElems.length; i++) {
				var audioElem = childrenAudioElems[i];
				if(currentPlayAllAudioElem == audioElem) {
					/* Playing all results, and the currently playing element will be hidden*/
					hidingPlayAll = true;
				} else if (audioElem.pause && !audioElem.paused) {
					/* Pause this as it will be hidden */
					audioElem.pause();
				}
				audioElem.className += " hidden";
			}
			if(hidingPlayAll) {
				if (playAllIcon.className == "glyphicon glyphicon-play") {
					/* Stop playing all */
					updatePlayAllButton(false);
					playerElem.setAttribute("data-current-track", -1);
				} else {
					/* Continue playing all to an element that is not hidden */
					playAllNext(playerElem);
				}
			}
		} else {
			/* Additionnaly we modify the aria-expanded state for improved accessiblity */
			button.setAttribute("aria-expanded", "true");
			button.title = button.getAttribute("data-title-expanded");
			expandableAudiosContainer.className = expandableAudiosContainer.className.replace("hidden", "");
			if(hiddenCountElem != null) {
				hiddenCountElem.className += " hidden";
			}
			if(evenMoreElem != null) {
				evenMoreElem.className = evenMoreElem.className.replace("hidden", "");
			}
			for(var j = 0; j < childrenAudioElems.length; j++) {
				childrenAudioElems[j].className = childrenAudioElems[j].className.replace("hidden", "");
			}
		}
	}
}