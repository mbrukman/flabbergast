<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="http://www.w3.org/1999/xhtml" xmlns:o_0="http://flabbergast.org/api" version="1.0" exclude-result-prefixes="o_0">
  <xsl:output method="html" doctype-system="about:legacy-compat" encoding="UTF-8" indent="yes"/>
  <xsl:variable name="apos">'</xsl:variable>
  <xsl:template match="/">
    <html>
      <head>
        <meta http-equiv="content-type" content="text/xml; charset=utf-8"/>
        <title>Flabbergast Documentation</title>
        <link rel="stylesheet" type="text/css" href="o_0.css"/>
        <link rel="stylesheet" type="text/css" href="o_0-index.css"/>
      </head>
      <body>
        <h1><a href="http://flabbergast.org/">Flabbergast</a> Flabbergast Documentation</h1>
<div id="container"><div id="content">
        <p>Is Flabbergast making you go o_0? If so, start with <a href="https://github.com/flabbergast-config/flabbergast/blob/master/flabbergast-manual.md">the Flabbergast Manual</a>. If you just want to know the name of that template thing that does the stuff, this the right place.</p>
        <h2>Libraries</h2>
        <table>
          <thead>
            <tr>
              <th>URI</th>
              <th>Description</th>
            </tr>
          </thead>
          <tbody>
            <xsl:apply-templates select="//o_0:ref_link">
              <xsl:sort select="@name"/>
            </xsl:apply-templates>
          </tbody>
        </table>
        <h2>Manual Pages</h2>
        <table>
          <thead>
            <tr>
              <th>Section</th>
              <th>Name</th>
              <th>Description</th>
              <th>Download</th>
            </tr>
          </thead>
          <tbody>
            <xsl:apply-templates select="//o_0:man">
              <xsl:sort select="@section" data-type="number"/>
              <xsl:sort select="@name"/>
            </xsl:apply-templates>
          </tbody>
        </table>
</div></div>
      </body>
    </html>
  </xsl:template>
  <xsl:template match="o_0:man">
    <tr>
      <td>
        <xsl:attribute name="title">
          <xsl:choose>
            <xsl:when test="@section = 1">General commands</xsl:when>
            <xsl:when test="@section = 2">System calls</xsl:when>
            <xsl:when test="@section = 3">Library functions</xsl:when>
            <xsl:when test="@section = 4">Special files</xsl:when>
            <xsl:when test="@section = 5">File formats and conventions</xsl:when>
            <xsl:when test="@section = 6">Games and screensavers</xsl:when>
            <xsl:when test="@section = 7">Miscellanea</xsl:when>
            <xsl:when test="@section = 8">System administration commands and daemons</xsl:when>
            <xsl:otherwise>Unknown</xsl:otherwise>
          </xsl:choose>
        </xsl:attribute>
        <xsl:value-of select="@section"/>
      </td>
      <td>
        <a href="{concat(@name, '.', @section, '.html')}">
          <xsl:value-of select="@name"/>
        </a>
      </td>
      <td>
        <xsl:value-of select="@description"/>
      </td>
      <td>
        <a href="{concat(@name, '.', @section, '.pdf')}">PDF</a>
      </td>
    </tr>
  </xsl:template>
  <xsl:template match="o_0:ref_link">
    <tr>
      <td>
        <a href="{concat('doc-', translate(@name, '/', '-'), '.xml')}">lib:<xsl:value-of select="@name"/></a>
      </td>
      <td>
        <xsl:copy-of select="*|text()"/>
      </td>
    </tr>
  </xsl:template>
</xsl:stylesheet>
