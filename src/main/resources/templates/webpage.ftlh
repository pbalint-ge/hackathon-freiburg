<#setting datetime_format="yyyy-MM-dd HH:mm:ss">
<html>
<head>
<style>
th {
  text-align: left
}
</style>
</head>
<body>
    <table style="width:100%">
    <tr>
        <th>Id</th>
        <th>Modality</th>
        <th>Start date</th>
        <th>End date</th>
        <th>Priority</th>
        <th>Status</th>
        <th>First name</th>
        <th>Last name</th>
        <th>Sex</th>
        <th>Date of birth</th>
    </tr>
    <#list appointments as appointment>
        <tr>
            <td>${appointment.id!'N/A'}</td>
            <td>${appointment.modality!'N/A'}</td>
            <td>${appointment.startDate!'N/A'}</td>
            <td>${appointment.endDate!'N/A'}</td>
            <td>${appointment.priority!'N/A'}</td>
            <td>${appointment.status!'N/A'}</td>
            <td>${appointment.patient.firstName!'N/A'}</td>
            <td>${appointment.patient.lastName!'N/A'}</td>
            <td>${appointment.patient.sex!'N/A'}</td>
            <td>${appointment.patient.birthDate!'N/A'}</td>
        </tr>
    </#list>
    </table>
<body>
</html>