<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="functions" uri="http://www.jahia.org/tags/functions" %>
<%@ taglib prefix="jcr" uri="http://www.jahia.org/tags/jcr" %>
<%@ taglib prefix="s" uri="http://www.jahia.org/tags/search" %>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>
<%@ taglib prefix="utility" uri="http://www.jahia.org/tags/utilityLib" %>
<%@ taglib prefix="crawl" uri="http://www.jahia.org/tags/crawler" %>
<%@page import="java.lang.System"%>
<%--@elvariable id="currentNode" type="org.jahia.services.content.JCRNodeWrapper"--%>
<%--@elvariable id="out" type="java.io.PrintWriter"--%>
<%--@elvariable id="script" type="org.jahia.services.render.scripting.Script"--%>
<%--@elvariable id="scriptInfo" type="java.lang.String"--%>
<%--@elvariable id="workspace" type="java.lang.String"--%>
<%--@elvariable id="renderContext" type="org.jahia.services.render.RenderContext"--%>
<%--@elvariable id="currentResource" type="org.jahia.services.render.Resource"--%>
<%--@elvariable id="url" type="org.jahia.services.render.URLGenerator"--%>
<template:addResources type="css" resources="searchresults.css"/>

<c:if test="${renderContext.editMode}">
	<fieldset>
		<legend>${fn:escapeXml(jcr:label(currentNode.primaryNodeType,currentResource.locale))}</legend>
</c:if>
<%long startTime = System.currentTimeMillis(); %>
<c:set var="hitsName" value="hits_${currentNode.identifier}"/>
<c:set var="hitsCountName" value="hitsCount_${currentNode.identifier}"/>
<c:choose>
    <c:when test='${empty searchMap[hitsName] }'>
        <crawl:results var="resultsHits">
            <c:set target="${moduleMap}" property="listTotalSize" value="${count}" />
            <c:set target="${moduleMap}" property="resultsHits" value="${resultsHits}" />
            <c:if test='${searchMap == null}'>
                <jsp:useBean id="searchMap" class="java.util.HashMap" scope="request"/>
            </c:if>
            <c:set target="${searchMap}" property="${hitsName}" value="${resultsHits}"/>
            <c:set target="${searchMap}" property="${hitsCountName}" value="${count}"/>
        </crawl:results>
    </c:when>
    <c:otherwise>
        <c:set target="${moduleMap}" property="listTotalSize" value="${searchMap[hitsCountName]}" />
        <c:set target="${moduleMap}" property="resultsHits" value="${searchMap[hitsName]}" />
    </c:otherwise>
</c:choose>

<jcr:nodeProperty name="jcr:title" node="${currentNode}" var="title"/>
<div id="${currentNode.UUID}">
<div class="resultsList">
	<c:if test="${moduleMap['listTotalSize'] > 0}">
        <c:set var="termKey" value="src_terms[0].term"/>
        <h3><fmt:message key="search.results.found"><fmt:param value="${fn:escapeXml(param[termKey])}"/><fmt:param value="${moduleMap['listTotalSize']}"/></fmt:message></h3>
        <c:set var="beginName" value="begin_${currentNode.identifier}"/>
        <c:set var="endName" value="end_${currentNode.identifier}"/>
        <c:if test="${not empty requestScope[beginName]}">
            <c:set target="${moduleMap}" property="begin" value="${requestScope[beginName]}"/>
        </c:if>
        <c:if test="${not empty requestScope[endName]}">
            <c:set target="${moduleMap}" property="end" value="${requestScope[endName]}"/>
        </c:if>
      	<ol start="${moduleMap.begin+1}">
			<s:resultIterator begin="${moduleMap.begin}" end="${moduleMap.end}" varStatus="status" hits="${moduleMap['resultsHits']}">
				<li><%--<span>${status.index+1}.</span>--%><%@ include file="searchHit.jspf" %></li>
			</s:resultIterator>
        </ol>
        <div class="clear"></div>
	</c:if>
    <c:if test="${moduleMap['listTotalSize'] == 0}">
       	<h4><fmt:message key="search.results.no.results"/></h4>
	</c:if>
</div>
</div>

<% pageContext.setAttribute("searchTime", Long.valueOf(System.currentTimeMillis() - startTime)); %>
<utility:logger level="info" value="Search render time: ${searchTime} ms"/>
<c:if test="${renderContext.editMode}">
	</fieldset>
</c:if>
