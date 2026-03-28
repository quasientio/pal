<#--

    Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

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
