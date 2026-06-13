# MySQL 容器初始化脚本说明

本目录用于挂载到 MySQL 官方镜像容器内的 `/docker-entrypoint-initdb.d`。

## 初始化执行机制

MySQL 官方镜像在容器首次启动时，会检查数据目录是否为空。只有在数据卷为空、数据库尚未初始化的情况下，才会自动执行 `/docker-entrypoint-initdb.d` 目录下的初始化脚本。

执行规则如下：

- 目录中的 `.sql`、`.sh` 文件会按文件名顺序自动执行。
- 当前 schema 脚本命名为 `01-schema.sql`，用于确保表结构初始化顺序明确。
- 如果数据卷已经存在数据，这些初始化脚本不会再次执行。

因此，如果修改了 `01-schema.sql` 或原始 `kb-app/src/main/resources/sql/init.sql`，但容器里的表结构没有变化，通常是因为 MySQL 数据卷已经初始化过。需要重新初始化时，应先停止容器并删除对应数据卷，例如：

```bash
docker volume rm <mysql-data-volume-name>
```

删除数据卷会清空数据库中的全部数据，请确认没有需要保留的数据后再执行。

## 环境变量与用户权限

MySQL 官方镜像会读取 `MYSQL_USER`、`MYSQL_PASSWORD`、`MYSQL_DATABASE` 等环境变量，并在首次初始化时自动完成以下操作：

- 创建 `MYSQL_DATABASE` 指定的数据库。
- 创建 `MYSQL_USER` / `MYSQL_PASSWORD` 指定的普通用户。
- 自动授予该用户对 `MYSQL_DATABASE` 指定数据库的全部权限。

因此，本目录下的 SQL 初始化脚本只需要负责建库建表，不需要额外编写 `GRANT` 授权语句。
