<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="http://www.w3.org/1999/xhtml" xmlns:o_0="http://flabbergast.org/api" version="1.0" exclude-result-prefixes="o_0">
  <xsl:output method="html" doctype-system="about:legacy-compat" encoding="UTF-8" indent="yes"/>
  <xsl:variable name="apos">'</xsl:variable>
  <xsl:key name="refs" match="//o_0:ref/text()" use="."/>
  <xsl:key name="terms" match="//o_0:def/text()|//o_0:use/text()" use="."/>
  <xsl:template match="/">
    <html>
      <head>
        <meta http-equiv="content-type" content="text/xml; charset=utf-8"/>
        <title>lib:<xsl:value-of select="o_0:lib/@o_0:name"/> – Flabbergast Documentation</title>
        <link href="https://fonts.googleapis.com/css?family=Quicksand" rel="stylesheet"/>
        <link rel="stylesheet" type="text/css" href="o_0.css"/>
        <style type="text/css" id="termcss"/>
        <script type="text/javascript" src="o_0.js"/>
        <script type="text/javascript">
          function getTerms() { return [ <xsl:for-each select="(//o_0:def/text()|//o_0:use/text())[generate-id() = generate-id(key('terms', .)[1])]"> "<xsl:value-of select="translate(., '.', '-')"/>", </xsl:for-each> ]; }
          function getLibraries() { return [ <xsl:for-each select="(//o_0:ref/text())[generate-id() = generate-id(key('refs', .)[1])]"> "<xsl:value-of select="translate(., '/', '-')"/>", </xsl:for-each> ]; }
        </script>
      </head>
      <body onload="pageLoad();">
        <h1><a href="http://flabbergast.org"><svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" id="svg2" version="1.1"><path d="m 2.7246094,2.8398438 0,4.40625 15.8686166,0 4.400915,-4.40625 z m 26.4355466,6.28125 -14.533203,14.4960942 11,-10.966797 0,16.511718 3.533203,0 z"/><path d="m 2.7246094,8.0426083 0,4.2347357 10.9179686,0 4.174189,-4.2347357 z m 22.1038756,5.5538997 -4.234735,4.071461 0,11.49414 4.234735,0 z"/><path d="m 2.7246094,12.872842 0,6.070517 10.2187496,0 0,10.21875 7.056846,0 0,-10.849814 -5.373252,5.304893 0,-6.128907 -6.1289061,0 4.5627051,-4.615439 z"/><path d="m 11.11447,16.61603 13.68324,-13.83636 4.53716,0 0,4.42262 -13.83635,13.79779 0,-4.38405 z"/><path d="m 12.51992,19.36555 0,9.85479 -9.8548,-9.85479 z"/></svg>
Flabbergast</a> lib:<xsl:value-of select="o_0:lib/@o_0:name"/></h1>
        <div id="container">
          <div id="pane">
            <div id="tabbar">
              <span onclick="openTab('searchtab')" id="searchtabbutton" title="Search">
                <svg xmlns="http://www.w3.org/2000/svg" version="1.1" x="0px" y="0px" viewBox="0 0 32 32" width="32" height="32">
                  <path d="m 28.743311,25.436954 -5.579993,-5.560818 c -0.0767,-0.0767 -0.164359,-0.136966 -0.246538,-0.19997 1.128599,-1.733989 1.788775,-3.799436 1.788775,-6.023763 0,-6.1114203 -4.952689,-11.0641093 -11.06411,-11.0641093 -6.1086822,0 -11.0641112,4.952689 -11.0641112,11.0641093 0,6.11142 4.955429,11.06411 11.0641112,11.06411 2.224327,0 4.289773,-0.660176 6.026502,-1.791515 0.063,0.08492 0.12053,0.167098 0.197231,0.2438 l 5.579993,5.560817 c 0.457466,0.454728 1.051898,0.682091 1.646331,0.682091 0.597171,0 1.191604,-0.227363 1.64633,-0.682091 0.912194,-0.909454 0.912194,-2.383207 0.0055,-3.292661 M 13.641445,21.881317 c -4.5363132,0 -8.2289162,-3.692602 -8.2289162,-8.228914 0,-4.5363123 3.692603,-8.2289143 8.2289162,-8.2289143 4.536312,0 8.226175,3.692602 8.226175,8.2289143 0.0027,4.536312 -3.689863,8.228914 -8.226175,8.228914 m 5.544381,-8.93018 c -0.43829,0 -0.794402,0.353372 -0.794402,0.794402 0,2.572221 -2.09284,4.665061 -4.66506,4.665061 -0.438292,0 -0.794403,0.353372 -0.794403,0.794402 0,0.438292 0.353372,0.794403 0.794403,0.794403 1.668245,0 3.237875,-0.649219 4.418521,-1.829865 1.180647,-1.180647 1.829866,-2.750277 1.829866,-4.418522 0.0055,-0.446509 -0.350633,-0.799881 -0.788925,-0.799881 l 0,0 z M 11.041831,8.0970637 c -1.3696602,0.651959 -2.4516912,1.775079 -3.0543412,3.1666533 -0.172578,0.402679 0.01369,0.871103 0.413637,1.04368 0.101355,0.04383 0.208188,0.06574 0.315022,0.06574 0.306803,0 0.59991,-0.178055 0.728659,-0.479381 0.449248,-1.038201 1.2600862,-1.876432 2.2818532,-2.3667703 0.3972,-0.189013 0.564299,-0.662915 0.375286,-1.057377 -0.189013,-0.391722 -0.662915,-0.56156 -1.060116,-0.372548 l 0,0 z"/>
                </svg>
              </span>
              <span onclick="openTab('refstab')" id="refstabbutton" title="Referenced Libraries">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" version="1.1" width="32" height="32">
                  <path d="m 14.674438,16.904593 -0.05647,-0.05664 c -1.319207,-1.319011 -3.477783,-1.319011 -4.7967657,0 l -3.0561787,3.056599 c -1.319011,1.319011 -1.319011,3.477531 0,4.79657 l 0.056442,0.05641 c 0.1097448,0.109969 0.2262435,0.209121 0.3466097,0.300677 L 8.2869345,23.939272 C 8.1565916,23.862288 8.0329465,23.770171 7.9209598,23.65824 l -0.056638,-0.05664 c -0.7162276,-0.716003 -0.7162276,-1.881467 0,-2.59775 l 3.0565151,-3.056263 c 0.716283,-0.716284 1.881495,-0.716284 2.597666,0 l 0.05664,0.05639 c 0.716032,0.716283 0.716032,1.881747 0,2.59775 l -1.382823,1.382823 c 0.240032,0.592835 0.353784,1.22468 0.343863,1.85504 l 2.138342,-2.138341 c 1.319039,-1.319068 1.319039,-3.477531 -8.5e-5,-4.796654 z m -4.51691,4.403971 C 10.047783,21.19882 9.9312843,21.099472 9.8108902,21.008139 l -1.1188585,1.118635 c 0.1303429,0.07701 0.2539599,0.169185 0.3659747,0.281116 l 0.056638,0.05661 c 0.7162555,0.716283 0.7162555,1.881494 0,2.597722 l -3.056543,3.056571 c -0.7162836,0.716115 -1.881523,0.716115 -2.5977225,0 l -0.056638,-0.05664 c -0.7160034,-0.71634 -0.7160034,-1.881523 0,-2.597722 L 4.7865922,24.081609 C 4.5465325,23.488831 4.4328362,22.856902 4.442757,22.226598 l -2.1383134,2.138257 c -1.31906706,1.318955 -1.31906706,3.477558 0,4.796766 l 0.056414,0.0565 c 1.3192913,1.318899 3.4777544,1.318899 4.7968215,0 l 3.0562352,-3.056347 c 1.319011,-1.319011 1.319011,-3.477838 0,-4.796821 l -0.05639,-0.05639 z M 16,3 4.2382811,9.1914061 l 0,2.4746099 23.5234379,0 0,-2.4746099 L 16,3 Z m 0,2.4472656 A 2.3801653,2.2743802 0 0 1 18.380859,7.7226563 2.3801653,2.2743802 0 0 1 16,9.9960939 2.3801653,2.2743802 0 0 1 13.619141,7.7226563 2.3801653,2.2743802 0 0 1 16,5.4472656 Z m -9.2851562,8.6953124 0,4.191406 3.0058592,-2.921875 c 0.2030231,-0.197351 0.447581,-0.359483 0.707031,-0.501953 l 0,-0.767578 -3.7128902,0 z m 7.4277342,0 0,0.951172 c 0.464972,0.271022 0.931997,0.618996 1.388672,1.109375 2.164916,2.324691 2.292827,3.881277 0.898438,5.283203 l -1.314454,1.322266 2.742188,0 0,-8.666016 -3.714844,0 z m 7.429688,0 0,8.666016 3.71289,0 0,-8.666016 -3.71289,0 z M 12.650391,25.285156 8.9570311,29 l 18.8046879,0 0,-3.714844 -15.111328,0 z m -8.4121099,3.490235 0,0.224609 0.2148439,0 -0.2148439,-0.224609 z"/>
                </svg>
              </span>
              <xsl:if test="document('index.xml')//o_0:ref_link">
                <span onclick="openTab('indextab')" id="indextabbutton" title="All Libraries">
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" version="1.1" width="32" height="32">
                    <path d="M 18.380165,7.7223129 A 2.3801653,2.2743802 0 0 1 16,9.9966931 2.3801653,2.2743802 0 0 1 13.619835,7.7223129 2.3801653,2.2743802 0 0 1 16,5.4479327 2.3801653,2.2743802 0 0 1 18.380165,7.7223129 Z m -11.6658796,6.4205441 0,8.666667 3.7142856,0 0,-8.666667 -3.7142856,0 z m 7.4285716,0 0,8.666667 3.714286,0 0,-8.666667 -3.714286,0 z M 4.2380949,29 l 23.5238101,0 0,-3.714286 -23.5238101,0 0,3.714286 z m 17.3333331,-14.857143 0,8.666667 3.714286,0 0,-8.666667 -3.714286,0 z M 16,3 4.2380949,9.190476 l 0,2.476191 23.5238101,0 0,-2.476191 L 16,3 Z"/>
                  </svg>
                </span>
              </xsl:if>
              <xsl:if test="document('index.xml')//o_0:man">
                <span onclick="openTab('mantab')" id="mantabbutton" title="Manual Pages">
                  <svg xmlns="http://www.w3.org/2000/svg" version="1.1" x="0px" y="0px" viewBox="0 0 32 32" width="32" height="32"><path d="m 7.8243295,1.2282395 0,4.785114 -1.365348,0 c -0.170135,0 -0.308094,0.1379582 -0.308094,0.3080937 0,0.1701349 0.137959,0.3080938 0.308094,0.3080938 l 1.365348,0 0,7.353486 -1.365348,0 c -0.170135,0 -0.308094,0.138237 -0.308094,0.308371 0,0.170136 0.137959,0.307955 0.308094,0.307955 l 1.365348,0 0,7.842264 -1.365348,0 c -0.170135,0 -0.308094,0.138236 -0.308094,0.308371 0,0.169922 0.137959,0.308093 0.308094,0.308093 l 1.365348,0 0,4.784836 17.0087005,0 c 0.561043,0 1.016082,-0.455179 1.016082,-1.01622 l 0,-24.5822362 c 0,-0.5610412 -0.45504,-1.0162213 -1.016083,-1.0162213 l -17.0086995,0 z"/><polygon style="fill:#d14848" points="304.083,463.421 322.477,449.115 340.874,463.421 340.874,49.062 322.477,49.062 304.083,49.062 " transform="matrix(0.07121617,0,0,0.07121617,-10.613566,-2.2313082)"/></svg>
                </span>
              </xsl:if>
            </div>
            <div id="searchtab" class="tab">
              <div id="searcharea">
                <input type="text" id="search" onchange="searchChange();" onkeypress="searchChange();" onpaste="searchChange();" oninput="searchChange();"/>
                <span onclick="searchClear()" class="button">⌫</span>
                <br/>
                <span id="showPartials" onclick="toggleSelection(this);" title="If an exact match is found, display any partial matches.">Partial</span>
                <span id="hideExternals" onclick="toggleSelection(this);" title="Only display matches for this library, not libraries it uses.">Local</span>
              </div>
              <div id="terms">
                <div id="nomatches" style="display: none">No matches.</div>
                <xsl:apply-templates mode="searchpane" select="o_0:lib//o_0:attr"/>
              </div>
            </div>
            <div id="refstab" class="tab">
              <div id="references">
                <xsl:if test="not(//o_0:ref)">
                  <p>No external references.</p>
                </xsl:if>
                <xsl:for-each select="//o_0:ref/text()[generate-id() = generate-id(key('refs', .)[1])]">
                  <xsl:sort select="."/>
                  <a href="{concat('doc-', translate(., '/', '-'), '.xml')}" id="{concat('lib-', translate(., '/', '-'))}">lib:<xsl:value-of select="."/></a>
                </xsl:for-each>
              </div>
            </div>
            <xsl:if test="document('index.xml')//o_0:ref_link">
              <div id="indextab" class="tab">
                <div id="index">
                  <xsl:variable name="this" select="/o_0:lib/@o_0:name"/>
                  <xsl:for-each select="document('index.xml')//o_0:ref_link">
                    <xsl:sort select="@name"/>
                    <a href="{concat('doc-', translate(@name, '/', '-'), '.xml')}" title="{text()}"><xsl:if test="@name = $this"><xsl:attribute name="style">font-weight:bold</xsl:attribute></xsl:if>
lib:<xsl:value-of select="@name"/></a>
                  </xsl:for-each>
                </div>
              </div>
            </xsl:if>
            <xsl:if test="document('index.xml')//o_0:man">
              <div id="mantab" class="tab">
                <div id="man">
                  <xsl:for-each select="document('index.xml')//o_0:man">
                    <xsl:sort select="@section"/>
                    <xsl:sort select="@name"/>
                    <a href="{concat(@name, '.', @section, '.html')}" title="{@description}"><xsl:value-of select="@name"/> (<xsl:value-of select="@section"/>)</a>
                  </xsl:for-each>
                </div>
              </div>
            </xsl:if>
          </div>
          <div id="content">
            <p>
              <xsl:apply-templates select="o_0:lib/o_0:description/*|o_0:lib/o_0:description/text()"/>
            </p>
            <dl>
              <xsl:apply-templates select="o_0:lib/o_0:attr"/>
            </dl>
          </div>
        </div>
      </body>
    </html>
  </xsl:template>
  <xsl:template match="o_0:attr" mode="searchpane">
    <xsl:variable name="id">item-<xsl:for-each select="./ancestor::o_0:attr"><xsl:value-of select="@o_0:name"/>-</xsl:for-each><xsl:value-of select="@o_0:name"/></xsl:variable>
    <a href="{concat('#', $id)}" onclick="{concat('expandAll(', $apos, $id, $apos, ')')}"><xsl:attribute name="class"><xsl:for-each select="o_0:def[../@o_0:informative != 'true']|o_0:use|o_0:description//o_0:use"><xsl:value-of select="concat(local-name(), '_', translate(text(), '.', '-'), ' ')"/></xsl:for-each><xsl:if test="not(o_0:use|o_0:description//o_0:use)"> usenone</xsl:if><xsl:if test="@o_0:informative = 'true'"> defnone</xsl:if></xsl:attribute><xsl:for-each select="./ancestor::o_0:attr"><xsl:value-of select="concat(@o_0:name, substring(' .', 1 + count(o_0:type[text() = 'Frame']), 1))"/></xsl:for-each><xsl:value-of select="@o_0:name"/> (<xsl:value-of select="concat(@o_0:startline, ':', @o_0:startcol, '-', @o_0:endline, ':', @o_0:endcol)"/>)</a>
  </xsl:template>
  <xsl:template match="o_0:attr">
    <xsl:variable name="id">item-<xsl:for-each select="./ancestor::o_0:attr"><xsl:value-of select="@o_0:name"/>-</xsl:for-each><xsl:value-of select="@o_0:name"/></xsl:variable>
    <xsl:variable name="children" select="./o_0:attr"/>
    <dt id="{$id}">
      <xsl:if test="not(.//o_0:description) and ./o_0:attr">
        <xsl:attribute name="class">hidden</xsl:attribute>
      </xsl:if>
      <xsl:choose>
        <xsl:when test="not(./o_0:attr)">
          <span class="terminal">■</span>
        </xsl:when>
        <xsl:otherwise>
          <span class="roll" onclick="showHide(this);">
            <span/>
          </span>
        </xsl:otherwise>
      </xsl:choose>
      <xsl:choose>
        <xsl:when test="@o_0:informative = 'true'">
          <span class="def" onclick="{concat('showDef(', $apos, @o_0:name, $apos, ');')}" title="Find definitions">
            <xsl:value-of select="@o_0:name"/>
          </span>
          <xsl:text> </xsl:text>
        </xsl:when>
        <xsl:otherwise>
          <span class="use" onclick="{concat('showUse(', $apos, @o_0:name, $apos, ');')}" title="Find uses">
            <xsl:value-of select="@o_0:name"/>
          </span>
          <xsl:text> </xsl:text>
        </xsl:otherwise>
      </xsl:choose>
      <div class="info">
        <xsl:if test="count(o_0:type) &lt; 9">
          <xsl:apply-templates select="o_0:type"/>
        </xsl:if>
        <xsl:choose>
          <xsl:when test="/o_0:lib/@o_0:github">
            <a href="{concat(/o_0:lib/@o_0:github, '#L', @o_0:startline, '-L', @o_0:endline)}" title="View Source">
              <xsl:value-of select="concat(/o_0:lib/@o_0:name, '.o_0:', @o_0:startline, ':', @o_0:startcol, '-', @o_0:endline, ':', @o_0:endcol)"/>
            </a>
          </xsl:when>
          <xsl:otherwise>
            <xsl:value-of select="concat(/o_0:lib/@o_0:name, '.o_0:', @o_0:startline, ':', @o_0:startcol, '-', @o_0:endline, ':', @o_0:endcol)"/>
          </xsl:otherwise>
        </xsl:choose>
      </div>
    </dt>
    <dd>
      <xsl:if test="o_0:description/*|o_0:description/text()|o_0:use[not(text() = $children/@o_0:name)]|o_0:attr">
        <xsl:attribute name="class">populated</xsl:attribute>
      </xsl:if>
      <xsl:apply-templates select="o_0:description/*|o_0:description/text()"/>
      <xsl:if test="o_0:use[not(text() = $children/@o_0:name)]">
        <div class="uses">Uses:
        <xsl:for-each select="o_0:use[not(text() = $children/@o_0:name)]"><span onclick="{concat('showDef(', $apos, translate(text(), '.', '-'), $apos, ');')}" title="Find definitions" class="deflink"><xsl:value-of select="text()"/></span><xsl:text> </xsl:text></xsl:for-each>
      </div>
      </xsl:if>
      <xsl:if test="o_0:attr">
        <dl>
          <xsl:apply-templates select="o_0:attr"/>
        </dl>
      </xsl:if>
    </dd>
  </xsl:template>
  <xsl:template match="o_0:type[text()='Bin']">
    <span class="type" title="Bin">�</span>
  </xsl:template>
  <xsl:template match="o_0:type[text()='Bool']">
    <span class="type" title="Bool">◑</span>
  </xsl:template>
  <xsl:template match="o_0:type[text()='Float']">
    <span class="type" title="Float">ℝ</span>
  </xsl:template>
  <xsl:template match="o_0:type[text()='Frame']">
    <span class="type" title="Frame">◰</span>
  </xsl:template>
  <xsl:template match="o_0:type[text()='Int']">
    <span class="type" title="Int">ℤ</span>
  </xsl:template>
  <xsl:template match="o_0:type[text()='LookupHandler']">
    <span class="type" title="LookupHandler">👁️</span>
  </xsl:template>
  <xsl:template match="o_0:type[text()='Null']">
    <span class="type" title="Null">∅</span>
  </xsl:template>
  <xsl:template match="o_0:type[text()='Str']">
    <span class="type" title="Str">A</span>
  </xsl:template>
  <xsl:template match="o_0:type[text()='Template']">
    <span class="type" title="Template">⬚</span>
  </xsl:template>
  <xsl:template match="o_0:use">
    <span onclick="{concat('showDef(', $apos, text(), $apos, ');')}" title="Find definitions" class="show">
      <xsl:value-of select="text()"/>
    </span>
    <xsl:text> </xsl:text>
  </xsl:template>
  <xsl:template match="o_0:ref">
    <a href="{concat('doc-', translate(text(), '/', '_'), '.xml')}">lib:<xsl:value-of select="text()"/></a>
    <xsl:text> </xsl:text>
  </xsl:template>
  <xsl:template match="o_0:ref">
    <span class="show" title="Documentation not available.">lib:<xsl:value-of select="text()"/></span>
  </xsl:template>
  <xsl:template match="text()|*[namespace-uri() = 'http://www.w3.org/1999/xhtml']">
    <xsl:element name="{local-name()}">
      <xsl:apply-templates select="*|@*|text()"/>
    </xsl:element>
  </xsl:template>
  <xsl:template match="text()|@*">
    <xsl:copy-of select="."/>
  </xsl:template>
</xsl:stylesheet>
