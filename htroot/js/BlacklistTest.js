/* Re-test after returning from the native editor without replaying a POST. */
document.addEventListener("DOMContentLoaded", function () {
    "use strict";
    var awaitingEditor = false;
    var leftPage = false;
    document.querySelectorAll("form.blacklistEdit").forEach(function (form) {
        form.addEventListener("submit", function () { awaitingEditor = true; });
    });
    window.addEventListener("blur", function () {
        if (awaitingEditor) leftPage = true;
    });
    window.addEventListener("focus", function () {
        if (!awaitingEditor || !leftPage) return;
        awaitingEditor = false;
        var retest = document.getElementById("blacklistTest").getAttribute("data-retest");
        if (retest) window.location.replace(retest);
    });
});
