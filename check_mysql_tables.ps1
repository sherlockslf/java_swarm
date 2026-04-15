# MySQL数据库检查脚本
# 使用MySQL Connector/NET或ODBC连接

$host_name = "rm-bp1v0aoc564umyqbp.mysql.rds.aliyuncs.com"
$port = 3306
$database = "aim_product_dev"
$username = "aim_user"
$password = "Aim@2026"

# 构建连接字符串
$connectionString = "Server=$host_name;Port=$port;Database=$database;Uid=$username;Pwd=$password;SslMode=Preferred;Connection Timeout=10;"

Write-Host "正在连接MySQL数据库..." -ForegroundColor Cyan
Write-Host "主机: $host_name" -ForegroundColor Cyan
Write-Host "数据库: $database" -ForegroundColor Cyan
Write-Host ""

try {
    # 尝试加载MySQL Connector/NET
    Add-Type -Path "C:\Program Files (x86)\MySQL\MySQL Connector NET 8.0\MySql.Data.dll" -ErrorAction SilentlyContinue
    
    if (-not ([System.AppDomain]::CurrentDomain.GetAssemblies() | Where-Object { $_.GetName().Name -eq "MySql.Data" })) {
        Write-Host "未找到MySQL Connector/NET，尝试使用ODBC..." -ForegroundColor Yellow
        
        # 尝试使用ODBC
        $odbcConnectionString = "Driver={MySQL ODBC 8.0 Unicode Driver};Server=$host_name;Port=$port;Database=$database;Uid=$username;Pwd=$password;Option=3;"
        
        try {
            $connection = New-Object System.Data.Odbc.OdbcConnection($odbcConnectionString)
            $connection.Open()
            Write-Host "通过ODBC连接成功！" -ForegroundColor Green
            $useOdbc = $true
        } catch {
            Write-Host "ODBC连接失败: $_" -ForegroundColor Red
            Write-Host "请安装MySQL Connector/NET或MySQL ODBC驱动" -ForegroundColor Red
            exit 1
        }
    } else {
        $connection = New-Object MySql.Data.MySqlClient.MySqlConnection($connectionString)
        $connection.Open()
        Write-Host "通过MySQL Connector连接成功！" -ForegroundColor Green
        $useOdbc = $false
    }
    
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "1. 查询所有ai_prompt_前缀的表" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    
    if ($useOdbc) {
        $command = $connection.CreateCommand()
        $command.CommandText = "SHOW TABLES LIKE 'ai_prompt_%'"
        $adapter = New-Object System.Data.Odbc.OdbcDataAdapter($command)
        $dataTable = New-Object System.Data.DataTable
        $adapter.Fill($dataTable) | Out-Null
    } else {
        $command = New-Object MySql.Data.MySqlClient.MySqlCommand("SHOW TABLES LIKE 'ai_prompt_%'", $connection)
        $reader = $command.ExecuteReader()
        $tables = @()
        while ($reader.Read()) {
            $tables += $reader.GetString(0)
        }
        $reader.Close()
    }
    
    if ($useOdbc) {
        $tables = @()
        foreach ($row in $dataTable.Rows) {
            $tables += $row[0]
        }
    }
    
    Write-Host "找到 $($tables.Count) 个ai_prompt_前缀的表:" -ForegroundColor Yellow
    foreach ($table in $tables) {
        Write-Host "  - $table" -ForegroundColor White
    }
    
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "2. 检查每个表的数据量" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    
    $targetTables = @(
        "ai_prompt_users",
        "ai_prompt_psu",
        "ai_prompt_json_schemas",
        "ai_prompt_prompt_fragments",
        "ai_prompt_version_reviews",
        "ai_prompt_system_configs",
        "ai_prompt_audit_logs",
        "ai_prompt_test_datasets"
    )
    
    foreach ($table in $targetTables) {
        try {
            if ($useOdbc) {
                $cmd = $connection.CreateCommand()
                $cmd.CommandText = "SELECT COUNT(*) FROM $table"
                $count = $cmd.ExecuteScalar()
            } else {
                $cmd = New-Object MySql.Data.MySqlClient.MySqlCommand("SELECT COUNT(*) FROM $table", $connection)
                $count = $cmd.ExecuteScalar()
            }
            
            $tableExists = $tables -contains $table
            $status = if ($tableExists) { "存在" } else { "不存在" }
            Write-Host "$table : $count 条记录 ($status)" -ForegroundColor $(if ($tableExists) { "Green" } else { "Red" })
        } catch {
            Write-Host "$table : 查询失败 - $_" -ForegroundColor Red
        }
    }
    
    $connection.Close()
    Write-Host ""
    Write-Host "数据库检查完成！" -ForegroundColor Green
    
} catch {
    Write-Host "连接失败: $_" -ForegroundColor Red
    Write-Host "错误详情: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
