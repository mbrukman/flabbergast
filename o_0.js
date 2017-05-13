/* exported expandAll pageLoad searchChange searchClear showDef showHide showUse toggleSelection */
"use strict";

var validLookup = /^([a-z][a-zA-Z0-9_]*(\.[a-z][a-zA-Z0-9_]*)*)?$/;

function checkNoMatches() {
    var searchlist = document.getElementById("terms").getElementsByTagName("a");
    var any_visible = false;
    for (var it = 0; it < searchlist.length; it++) {
        if (window.getComputedStyle(searchlist[it]).getPropertyValue("display") != "none") {
            any_visible = true;
            break;
        }
    }
    document.getElementById("nomatches").style.display = any_visible ? "none" : "block";
}

function cssForArray(arr, css) {
    if (arr.length == 0) {
        return "";
    }
    return arr.map(function(sel) {
        return sel + "{" + css + "}";
    }).join("\n");
}

function expandAll(id) {
    for (var target = document.getElementById(id); target != null && target.nodeName == "DT"; target = target.parentElement.parentElement.previousElementSibling) {
        if (target.className == "hidden") {
            target.className = null;
        }
    }
    return true;
}

function pageLoad() {
    updateSelection("showPartials");
    updateSelection("hideExternals");
    openTab('searchtab');
    var showTerm = function() {
        var term = document.location.hash;
        if (term.startsWith("#term-")) {
            term = term.substring(6);
            var searchbox = document.getElementById("search");
            searchbox.value = term;
            searchChange();
        } else if (term.startsWith("#item-")) {
            expandAll(term.substring(1));
            // Bug: https://bugzilla.mozilla.org/show_bug.cgi?id=645075
            if (navigator.userAgent.indexOf("Firefox") > -1) {
                location.href += "";
            }
        }
    };
    var showLoadError = function(context, message) {
        var p = document.createElement("p");
        p.appendChild(document.createTextNode(context + ": " + (message || "Unknown error")));
        var refsTab = document.getElementById("refstab");
        refsTab.insertBefore(p, refsTab.firstChild);
    };
    var libraryNames = getLibraries();
    if (libraryNames.length == 0) {
        showTerm();
        return;
    }
    var makeLibraryInfo = function(name) {
        return {
            "name": name,
            "nameParts": name.split("-")
        };
    };
    var libraries = libraryNames.map(makeLibraryInfo);
    var inflight = libraries.length + 1;
    var unref = function() {
        if (--inflight > 0) {
            return;
        }
        var searchlistdiv = document.getElementById("terms").getElementsByTagName("div")[0];
        var sortedLibraries = libraries.sort(function(a, b) {
            var result = 0;
            for (var i = 0; result == 0 && i < a.nameParts.length && i < b.nameParts.length; i++) {
                result = a.nameParts[i].localeCompare(b.nameParts[i]);
            }
            return result || (a.nameParts.length - b.nameParts.length);
        });
        sortedLibraries.filter(function(library) {
            return library.links;
        }).forEach(function(library) {
            searchlistdiv.appendChild(library.links);
        });
        sortedLibraries.filter(function(library) {
            return library.error;
        }).forEach(function(library) {
            var link = document.getElementById("lib-" + library.name);
            if (link) {
                link.className = "error";
            }
            showLoadError(library.name.replace("-", "/"), library.error);
        });
        showTerm();
    };

    var termsForExternal = new XSLTProcessor();
    termsForExternal.setParameter(null, "knownterms", getTerms().map(function(t) {
        return "[" + t + "]";
    }).join(""));
    var downloadLibrary = function(info) {
        var request = new XMLHttpRequest();
        request.addEventListener("error", function() {
            unref();
            info.error = request.statusText || "Unknown error";
        });
        request.addEventListener("load", function() {
            info.links = termsForExternal.transformToFragment(request.responseXML, document);
            var nsResolver = request.responseXML.createNSResolver(request.responseXML.documentElement);
            var newLibraries = request.responseXML.evaluate("//o_0:ref/text()[not(contains(., 'interop'))]", request.responseXML.documentElement, nsResolver, XPathResult.UNORDERED_NODE_ITERATOR_TYPE, null);
            for (var newNameNode = newLibraries.iterateNext(); newNameNode; newNameNode = newLibraries.iterateNext()) {
                var newName = newNameNode.textContent.replace("/", "-");
                if (libraries.every(function(existing) {
                    existing.name != newName;
                })) {
                    var newInfo = makeLibraryInfo(newName);
                    libraries.push(newInfo);
                    inflight++;
                    downloadLibrary(newInfo);
                }
            }
            unref();
        });
        request.open("GET", "doc-" + info.name + ".xml", true);
        request.send();
    };
    var xsltRequest = new XMLHttpRequest();
    xsltRequest.addEventListener("error", function() {
        showTerm();
        showLoadError("External references", xsltRequest.statusText);
    });
    xsltRequest.addEventListener("load", function() {
        try {
            termsForExternal.importStylesheet(xsltRequest.responseXML);
        } catch(e) {
            showLoadError("External references", e);
            showTerm();
            return;
        }
        libraries.forEach(function(library) {
            try {
                downloadLibrary(library);
            } catch(e) {
                library.error = e;
                unref();
            }
        });
        unref();
    });
    xsltRequest.open("GET", "o_0-xref.xsl", true);
    xsltRequest.overrideMimeType("text/xml");
    xsltRequest.send();
}

function openTab(tab) {
    var i;
    var tabs = document.getElementsByClassName("tab");
    for (i = 0; i < tabs.length; i++) {
        tabs[i].style.display = "none";
    }
    var buttons = document.getElementById("tabbar").children;
    for (i = 0; i < buttons.length; i++) {
        buttons[i].className = "";
    }
    document.getElementById(tab).style.display = null;
    document.getElementById(tab + "button").className = "selected";
}

function searchClear() {
    var searchbox = document.getElementById("search");
    searchbox.value = "";
    searchbox.focus();
    searchChange();
}

function searchChange() {
    var searchbox = document.getElementById("search");
    var termcss = document.getElementById("termcss");
    if (searchbox.value.length == 0) {
        searchbox.className = null;
        termcss.innerHTML = "";
        updateRefs(null);
    } else if (validLookup.test(searchbox.value)) {
        var searchterm = searchbox.value.replace(".", "-");
        updateRefs(searchterm);
        searchbox.className = null;
        var exact_defs = [];
        var exact_uses = [];
        var starts_defs = [];
        var starts_uses = [];
        var contains_defs = [];
        var contains_uses = [];
        var unmatched = ["#terms a.defnone", "#terms a.usenone"];

        var known_terms = getTerms();
        for (var it = 0; it < known_terms.length; it++) {
            var def_sel = "#terms a.def_" + known_terms[it];
            var use_sel = "#terms a.use_" + known_terms[it];
            if (known_terms[it] == searchterm) {
                exact_defs.push(def_sel);
                exact_uses.push(use_sel);
            } else if (known_terms[it].startsWith(searchterm)) {
                starts_defs.push(def_sel);
                starts_uses.push(use_sel);
            } else if (known_terms[it].indexOf(searchterm) != -1) {
                contains_defs.push(def_sel);
                contains_uses.push(use_sel);
            } else {
                unmatched.push(def_sel);
                unmatched.push(use_sel);
            }
        }

        var cssParts;
        if (exact_defs.length > 0 || exact_uses.length > 0 || starts_defs.length > 0 || starts_uses.length > 0) {
            var showPartials = getSelection("showPartials") || (exact_defs.length == 0 && exact_uses.length == 0);
            cssParts = [{
                items: exact_defs,
                css: "font-weight: bold; color: #4F94CD; display: block !important;"
            },
            {
                items: exact_uses,
                css: "font-weight: bold; display: block !important;"
            },
            {
                items: starts_defs,
                css: showPartials ? "color: #4F94CD; display: block !important;" : "display: none;"
            },
            {
                items: starts_uses,
                css: showPartials ? "display: block !important;" : "display: none;"
            },
            {
                items: unmatched.concat(contains_defs, contains_uses),
                css: "display: none;"
            }];
        } else {
            cssParts = [{
                items: contains_defs,
                css: "font-weight: bold; color: #4F94CD; display: block !important;"
            },
            {
                items: contains_uses,
                css: "font-weight: bold; display: block !important;"
            },
            {
                items: unmatched,
                css: "display: none;"
            }];
        }

        termcss.innerHTML = cssParts.map(function(part) {
            return cssForArray(part.items, part.css);
        }).join("") + (getSelection("hideExternals") ? "\n#terms a.external { display: none !important; }" : "");
        checkNoMatches();
    } else {
        searchbox.className = "error";
    }
    return true;
}

function showDef(term) {
    showTerm(term, "def");
}

function showUse(term) {
    showTerm(term, "use");
}

function showTerm(term, prefix) {
    var termcss = document.getElementById("termcss");
    var visible = [];
    var hidden = ["#terms a." + prefix + "none"];
    var known_terms = getTerms();
    for (var it = 0; it < known_terms.length; it++) {
        (known_terms[it] == term ? visible : hidden).push("#terms a." + prefix + "_" + known_terms[it]);
    }
    termcss.innerHTML = cssForArray(visible, (prefix == "def" ? "color: #4F94CD; " : "") + "font-weight: bold; display: block !important;") + cssForArray(hidden, "display: none;");
    checkNoMatches();
}

function showHide(roller) {
    var is_hidden = roller.parentNode.className == "hidden";
    roller.parentNode.className = is_hidden ? "" : "hidden";
}

function getSelection(name) {
    return (window.localStorage.getItem(name) || "false") === "true";
}

function updateSelection(name) {
    var value = (window.localStorage.getItem(name) || "true") === "true";
    document.getElementById(name).className = value ? "option-on" : "option-off";
}

function toggleSelection(element) {
    var value = (window.localStorage.getItem(element.id) || "true") !== "true";
    element.className = value ? "option-on" : "option-off";
    window.localStorage.setItem(element.id, value ? "true" : "false");
    searchChange();
}

function updateRefs(term) {
    var hash_name = term == null ? null : ("term-" + term);
    var reflist = document.getElementById("references").childNodes;
    for (var it = 0; it < reflist.length; it++) {
        if (reflist[it].tagName == "A") {
            reflist[it].hash = hash_name;
        }
    }
}
