<#--

    Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>

    Use of this software is governed by the Business Source License 1.1
    included in the file LICENSE and at https://mariadb.com/bsl11

    Change Date: 2029-10-01
    Change License: Apache 2.0

-->
# Third-party notices

Pal incorporates the libraries listed below.

<#assign entries = (dependencyMap![])>
<#if entries?size == 0>
_No third-party dependencies were detected._
<#else>
<#list entries as e>
  <#assign p   = e.key>
  <#assign lic = (e.value)![]>
* **${p.groupId!"unknown"}:${p.artifactId!"unknown"}:${p.version!"unknown"}**  
  <#if lic?has_content>
    Licence: <#list lic as l>${l}<#if l_has_next>, </#if></#list>
  <#else>
    Licence: _unknown_
  </#if><#if p.url??> — ${p.url}</#if>
</#list>
</#if>
