$host_ip = "81.70.177.51"
$ports = @(
    @{Name="MySQL"; Port=3306},
    @{Name="Redis"; Port=6379},
    @{Name="MinIO API"; Port=9000},
    @{Name="MinIO Console"; Port=9001},
    @{Name="Milvus gRPC"; Port=19530}
)

foreach ($item in $ports) {
    $result = Test-NetConnection -ComputerName $host_ip -Port $item.Port -WarningAction SilentlyContinue
    $status = if ($result.TcpTestSucceeded) { "OK" } else { "FAILED" }
    Write-Host "$($item.Name) (port $($item.Port)): $status"
}
