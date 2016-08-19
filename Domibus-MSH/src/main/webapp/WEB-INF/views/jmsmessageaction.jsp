<%@page session="true" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>


<html>
<head>
    <jsp:include page="header.jsp"/>
</head>

<body>


<h1>JMS Message Action</h1>

<div>
    <c:out value="${messageResult}"/>
</div>


<a href="jmsmonitoring" onclick="history.go(-2);return false;">Return to JMS Monitoring</a>
</body>
</html>
