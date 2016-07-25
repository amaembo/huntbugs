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
  
  <xsl:template match="HuntBugs">
     <div>
       <xsl:if test="count(WarningList/Warning[@Status!='fixed'])>0">
         <div class="Tab" data-target="warnings-all">All warnings (<xsl:value-of select="count(WarningList/Warning[@Status!='fixed'])"/>)</div>
       </xsl:if>
       <xsl:if test="count(WarningList/Warning[@Status='added'])>0">
         <div class="Tab" data-target="warnings-added">Added (<xsl:value-of select="count(WarningList/Warning[@Status='added'])"/>)</div>
       </xsl:if>
       <xsl:if test="count(WarningList/Warning[@Status='changed'])>0">
         <div class="Tab" data-target="warnings-changed">Changed (<xsl:value-of select="count(WarningList/Warning[@Status='changed'])"/>)</div>
       </xsl:if>
       <xsl:if test="count(WarningList/Warning[@Status='score_raised'])>0">
         <div class="Tab" data-target="warnings-raised">Score raised (<xsl:value-of select="count(WarningList/Warning[@Status='score_raised'])"/>)</div>
       </xsl:if>
       <xsl:if test="count(WarningList/Warning[@Status='score_lowered'])>0">
         <div class="Tab" data-target="warnings-lowered">Score lowered (<xsl:value-of select="count(WarningList/Warning[@Status='score_lowered'])"/>)</div>
       </xsl:if>
       <xsl:if test="count(WarningList/Warning[@Status='fixed'])>0">
         <div class="Tab" data-target="warnings-fixed">Fixed (<xsl:value-of select="count(WarningList/Warning[@Status='fixed'])"/>)</div>
       </xsl:if>
       <xsl:if test="count(ErrorList/Error)>0">
         <div class="Tab" data-target="errors">Errors (<xsl:value-of select="count(ErrorList/Error)"/>)</div>
       </xsl:if>
     </div>
     <xsl:apply-templates/>
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

        function updateCount(tabContent) {
          var warnings = tabContent.getElementsByClassName("WarningsBody")[0].children;
          var total = 0, shown = 0;
          for(var i=0; i<warnings.length; i+=2) {
            total++;
            if(!/ Hidden$/.test(warnings[i].className))
              shown++;
          }
          tabContent.getElementsByClassName("WarningCount")[0].innerText = shown+"/"+total;
        }
        
        var tabRecords = [];
        
        function activateTab(tab) {
          for(var i=0; i<tabRecords.length; i++) {
            if(tabRecords[i][0] == tab) {
              tabRecords[i][0].className = "Tab";
              tabRecords[i][1].className = "TabContent Active";
            } else {
              tabRecords[i][0].className = "Tab Inactive";
              tabRecords[i][1].className = "TabContent";
            }
          }
        }
        
        function initTabs() {
          var tabs = document.getElementsByClassName("Tab");
          
          for(var i=0; i<tabs.length; i++) {
            var tab = tabs[i];
            var tabContent = document.getElementById(tab.getAttribute("data-target"));
            tabRecords.push([tab, tabContent]);
            (function(tab) { 
              tabs[i].addEventListener("click", function() {activateTab(tab);});
            })(tab);
            initTabContent(tabContent);
          }
          if(tabs.length > 0) {
            activateTab(tabs[0]);
          }
        }
        
        function initTabContent(tabContent) {
          var rows = tabContent.getElementsByClassName("WarningRow");
          for(var i=0; i<rows.length; i++) {
            var btns = rows[i].getElementsByClassName("hideWarning");
            if(btns.length == 0)
              continue;
            (function(clsName, btn) {
              btn.addEventListener("click", function() {
                var toHide = tabContent.getElementsByClassName("Warning-"+clsName);
                for(var j=0; j<toHide.length; j++) {
                  toggle(toHide[j]);
                }
                updateCount(tabContent);
              });
            })(/Warning-(\w+)/.exec(rows[i].className)[1], btns[0]);
          }
        }

        initTabs();
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
      
      .hideWarning {
        cursor: pointer;
        color: #55F;
        text-decoration: underline;
      }
      
      .WarningRow.Hidden {
        display: none;
      }
      
      .TabContent {
        display: none;
      }
      
      .TabContent.Active {
        display: block;
        border: 1px solid #DDD;
        padding: 3pt;
        clear: both;
      }
      
      .Tab {
        float: left;
        padding: 1em;
        background-color: #DDD;
        margin-right: 5pt;
      }
      
      .Tab.Inactive {
        background-color: #BBB;
        cursor: pointer;
      }
      </style>
    </head>
  </xsl:template>

  <xsl:template match="ErrorList">
    <div class="TabContent" id="errors">
      <table class="Errors"><thead><tr><th colspan="2">Errors (<xsl:value-of select="count(Error)"/>)</th></tr></thead>
        <tbody class="ErrorsBody Hidden">
          <xsl:apply-templates/>
        </tbody>
      </table>
    </div>
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
    <div id="warnings-all" class="TabContent">
    <table class="Warnings"><thead><tr><th colspan="2">All warnings (<span class="WarningCount"><xsl:value-of select="count(Warning[@Status!='fixed'])"/></span>)</th></tr></thead>
      <tbody class="WarningsBody">
        <xsl:apply-templates select="Warning[@Status!='fixed']"/>
      </tbody>
    </table>
    </div>
    <div id="warnings-added" class="TabContent">
    <table class="Warnings"><thead><tr><th colspan="2">Added (<span class="WarningCount"><xsl:value-of select="count(Warning[@Status='added'])"/></span>)</th></tr></thead>
      <tbody class="WarningsBody">
        <xsl:apply-templates select="Warning[@Status='added']"/>
      </tbody>
    </table>
    </div>
    <div id="warnings-changed" class="TabContent">
    <table class="Warnings"><thead><tr><th colspan="2">Changed (<span class="WarningCount"><xsl:value-of select="count(Warning[@Status='changed'])"/></span>)</th></tr></thead>
      <tbody class="WarningsBody">
        <xsl:apply-templates select="Warning[@Status='changed']"/>
      </tbody>
    </table>
    </div>
    <div id="warnings-raised" class="TabContent">
    <table class="Warnings"><thead><tr><th colspan="2">Score raised (<span class="WarningCount"><xsl:value-of select="count(Warning[@Status='score_raised'])"/></span>)</th></tr></thead>
      <tbody class="WarningsBody">
        <xsl:apply-templates select="Warning[@Status='score_raised']"/>
      </tbody>
    </table>
    </div>
    <div id="warnings-lowered" class="TabContent">
    <table class="Warnings"><thead><tr><th colspan="2">Score lowered (<span class="WarningCount"><xsl:value-of select="count(Warning[@Status='score_lowered'])"/></span>)</th></tr></thead>
      <tbody class="WarningsBody">
        <xsl:apply-templates select="Warning[@Status='score_lowered']"/>
      </tbody>
    </table>
    </div>
    <div id="warnings-fixed" class="TabContent">
    <table class="Warnings"><thead><tr><th colspan="2">Fixed (<span class="WarningCount"><xsl:value-of select="count(Warning[@Status='fixed'])"/></span>)</th></tr></thead>
      <tbody class="WarningsBody">
        <xsl:apply-templates select="Warning[@Status='fixed']"/>
      </tbody>
    </table>
    </div>
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
            <xsl:apply-templates select="Field"/>
            <xsl:apply-templates select="Annotation[@Role='VARIABLE']"/>
            <xsl:apply-templates select="LocationAnnotation[@Role='DEAD_CODE_LOCATION']"/>
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

  <xsl:template match="Field">
    <tr><th>Field:</th><td><xsl:value-of select="@Name"/></td></tr>
  </xsl:template>

  <xsl:template match="Annotation[@Role='VARIABLE']">
    <tr><th>Variable:</th><td><xsl:value-of select="text()"/></td></tr>
  </xsl:template>

  <xsl:template match="LocationAnnotation[@Role='DEAD_CODE_LOCATION']">
    <tr><th>Dead code at:</th><td><xsl:value-of select="@Line"/></td></tr>
  </xsl:template>
</xsl:stylesheet>
