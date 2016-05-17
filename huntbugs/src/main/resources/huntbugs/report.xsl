<?xml version="1.0"?>
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xlink="http://www.w3.org/1999/xlink"
  exclude-result-prefixes="xlink">

  <xsl:output doctype-public="-//W3C//DTD HTML 4.01 Transitional//EN"
    doctype-system="http://www.w3.org/TR/html4/loose.dtd"
    encoding="UTF-8"/>
 
  <xsl:template match="/">
    <html>
      <!-- HTML header -->
      <xsl:call-template name="make-html-header"/>
      <body>
        <h1>HuntBugs report</h1>
        <xsl:apply-templates/>
        <xsl:call-template name="make-scripts"/>
      </body>
    </html>
  </xsl:template>

  <xsl:template name="make-scripts">
    <script type="text/javascript"><![CDATA[
        function toggle(e) {
          if(/ Hidden$/.test(e.className)) {
            e.className = e.className.substring(0, e.className.length-' Hidden'.length);
          } else {
            e.className += ' Hidden';
          }
        }

        function updateCount() {
          var warnings = document.getElementsByClassName("WarningsBody")[0].children;
          var total = 0, shown = 0;
          for(var i=0; i<warnings.length; i+=2) {
            total++;
            if(!/ Hidden$/.test(warnings[i].className))
              shown++;
          }
          document.getElementsByClassName("WarningCount")[0].innerText = shown+"/"+total;
        }

        var errorList = document.getElementsByClassName("toggleErrors");
        if(errorList.length > 0) {
          errorList[0].addEventListener("click", function() {
            toggle(document.getElementsByClassName("toggleErrors")[0]);
            toggle(document.getElementsByClassName("ErrorsBody")[0]);
          });
        }
        
        var rows = document.getElementsByClassName("WarningRow");
        for(var i=0; i<rows.length; i++) {
          var btns = rows[i].getElementsByClassName("hideWarning");
          if(btns.length == 0)
            continue;
          (function(clsName, btn) {
            btn.addEventListener("click", function() {
              var toHide = document.getElementsByClassName("Warning-"+clsName);
              for(var j=0; j<toHide.length; j++) {
                toggle(toHide[j]);
              }
              updateCount();
            });
          })(/Warning-(\w+)/.exec(rows[i].className)[1], btns[0]);
        }
    ]]></script>
  </xsl:template>
  
  <xsl:template name="make-html-header">
    <head>
      <title>
        HuntBugs Report
      </title>
      <style>
      body {
        font-family: verdana, helvetica, sans-serif;
      }

      br {
        margin: 1em;
      }
      
      code {
        font-size: 140%;
      }
      
      span.WarningType {
        font-size: 70%;
        color: gray;
      }
      
      code.Member {
        border-bottom: 1px gray dotted;
      }
      
      .AnotherLocation {
        color: gray;
      }
      
      table.Warnings, table.Errors {
        border-collapse: collapse;
        margin: 3pt;
        width: 100%;
      }
      
      table.Warnings, table.Warnings > tbody > tr > td {
        border: 1px solid blue;
        padding: 3pt;
      }

      table.Errors thead {
        background-color: red;
        color: white;
      }
      
      table.Errors, table.Errors > tbody > tr > td {
        border: 1px solid red;
        padding: 3pt;
      }
      
      table.Errors > tbody > tr > td {
        vertical-align: top;
      }
      
      .Title {
        font-weight: bold;
      }
      
      td.Description {
        background-color: yellow;
        height: 10pt;
        vertical-align: top;
      }
      
      table.Properties th {
        text-align: right;
        font-weight: normal;
        font-size: 80%;
        color: #444;
      }
      
      .toggleErrors, .hideWarning {
        cursor: pointer;
        color: #55F;
        text-decoration: underline;
      }
      
      .ErrorsBody.Hidden, .WarningRow.Hidden {
        display: none;
      }
      
      .toggleErrors.Hidden:after {
        content: 'Hide';
      }
      
      .toggleErrors:after {
        content: 'Show';
      }
      </style>
    </head>
  </xsl:template>

  <xsl:template match="ErrorList">
    <table class="Errors"><thead><tr><th colspan="2">Errors (<xsl:value-of select="count(Error)"/>) [<span class="toggleErrors"></span>]</th></tr></thead>
      <tbody class="ErrorsBody Hidden">
        <xsl:apply-templates/>
      </tbody>
    </table>
  </xsl:template>

  <xsl:template match="Error">
    <tr>
        <td>
            <table class="Properties">
              <xsl:choose><xsl:when test="@Class"><tr><th>Class:</th><td><xsl:value-of select="@Class"/></td></tr></xsl:when></xsl:choose>
              <xsl:choose><xsl:when test="@Member"><tr><th>Member:</th><td><xsl:value-of select="@Member"/></td></tr></xsl:when></xsl:choose>
              <xsl:choose><xsl:when test="@Detector"><tr><th>Detector:</th><td><xsl:value-of select="@Detector"/></td></tr></xsl:when></xsl:choose>
            </table>
        </td>
        <td><pre><xsl:value-of select="."/></pre>
        </td>
    </tr>
  </xsl:template>
  
  <xsl:template match="WarningList">
    <table class="Warnings"><thead><tr><th colspan="2">Warnings (<span class="WarningCount"><xsl:value-of select="count(Warning)"/></span>)</th></tr></thead>
      <tbody class="WarningsBody">
        <xsl:apply-templates/>
      </tbody>
    </table>
  </xsl:template>

  <xsl:template match="Warning">
    <tr>
        <xsl:attribute name="class">WarningRow Warning-<xsl:value-of select="@Type"/></xsl:attribute>
        <td rowspan="2">
            <div class="Title"><xsl:value-of select="Title"/><br/><span class="WarningType">(<xsl:value-of select="@Type"/> [<span class="hideWarning" title="Hide this type of warnings">x</span>])</span></div>
            <table class="Properties">
            <tr><th>Category:</th><td><xsl:value-of select="@Category"/></td></tr>
            <tr><th>Score:</th><td><xsl:value-of select="@Score"/></td></tr>
            <xsl:apply-templates select="Location"/>
            <xsl:apply-templates select="Class"/>
            <xsl:apply-templates select="Method"/>
            </table>
        </td>
        <td class="Description">
            <div class="Description"><xsl:value-of select="Description"/></div>
        </td>
    </tr>
    <tr>
        <xsl:attribute name="class">WarningRow Warning-<xsl:value-of select="@Type"/></xsl:attribute>
        <td>
            <div class="LongDescription"><xsl:value-of select="LongDescription/text()" disable-output-escaping="yes"/></div>
        </td>
    </tr>
  </xsl:template>

  <xsl:template match="Location">
    <tr><th>Location:</th><td><xsl:value-of select="@SourceFile"/>:<xsl:value-of select="@Line"/><xsl:apply-templates select="../AnotherLocation"/></td></tr>
  </xsl:template>

  <xsl:template match="AnotherLocation">
    <span class="AnotherLocation">; <xsl:value-of select="@Line"/></span>
  </xsl:template>

  <xsl:template match="Class">
    <tr><th>Class:</th><td><xsl:value-of select="@Name"/></td></tr>
  </xsl:template>

  <xsl:template match="Method">
    <tr><th>Method:</th><td><xsl:value-of select="@Name"/></td></tr>
  </xsl:template>
</xsl:stylesheet>
