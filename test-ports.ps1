$host_ip = "81.70.177.51"
$ports = @(
    @{Name="MySQL"; Port=3306},
    @{Name="Redis"; Port=6379},
    @{Name="MinIO 接口"; Port=9000},
    @{Name="MinIO 控制台"; Port=9001},
    @{Name="Milvus gRPC"; Port=19530}
)

foreach ($item in $ports) {
    $result = Test-NetConnection -ComputerName $host_ip -Port $item.Port -WarningAction SilentlyContinue
    $status = if ($result.TcpTestSucceeded) { "正常" } else { "失败" }
    Write-Host "$($item.Name)（端口 $($item.Port)）：$status"
}
