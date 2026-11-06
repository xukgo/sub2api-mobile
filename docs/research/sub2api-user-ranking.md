# 官方 Sub2API 用户排行接口核验

核验日期：2026-11-07。基线版本为官方 `v0.2.19`，解析后的提交为 `dece26153621959ccd7635814f4b2d08304e681a`；另核对了当时官方 `main` 的 `0eee80ba87aa4f3aff7282bf1695aa6dc0471c36`。以下结论在两个版本中一致；未连接实际部署做性能测量。

官方网页的八个时间选项可以统一使用 `GET /api/v1/admin/dashboard/user-breakdown`，由数据库按用户聚合并返回排行，客户端无需分页下载使用记录。不过，网页标为“近 24 小时”的选项只传日期，并非精确滚动 24 小时。

## 网页如何请求排行

用量页把日期选择器产生的 `startDate/endDate` 写入筛选条件；`UserTokenRanking.vue` 调用 `getUserBreakdown`，传入 `start_date`、`end_date`、`sort_by`、`limit` 和可选筛选条件。API 地址为 `/admin/dashboard/user-breakdown`。通用 API 客户端为 GET 请求追加浏览器的 IANA 时区。

来源：[用量页日期处理](https://github.com/Wei-Shaw/sub2api/blob/dece26153621959ccd7635814f4b2d08304e681a/frontend/src/views/admin/UsageView.vue#L285)、[排行组件](https://github.com/Wei-Shaw/sub2api/blob/dece26153621959ccd7635814f4b2d08304e681a/frontend/src/components/admin/usage/UserTokenRanking.vue#L142)、[API 定义](https://github.com/Wei-Shaw/sub2api/blob/dece26153621959ccd7635814f4b2d08304e681a/frontend/src/api/admin/dashboard.ts#L168)、[时区请求拦截器](https://github.com/Wei-Shaw/sub2api/blob/0eee80ba87aa4f3aff7282bf1695aa6dc0471c36/frontend/src/api/client.ts#L32)。

`DateRangePicker.vue` 明确定义了以下八个选项，全部输出本地 `YYYY-MM-DD` 字符串：

| 选项 | 开始日期 | 结束日期 |
| --- | --- | --- |
| 今天 | 今天 | 今天 |
| 昨天 | 昨天 | 昨天 |
| 近 24 小时 | 当前时刻减 24 小时后所在的本地日期 | 今天 |
| 近 7 天 | 今天减 6 个日历日 | 今天 |
| 近 14 天 | 今天减 13 个日历日 | 今天 |
| 近 30 天 | 今天减 29 个日历日 | 今天 |
| 本月 | 本月第一天 | 今天 |
| 上月 | 上月第一天 | 上月最后一天 |

来源：[v0.2.19 日期格式与全部预设](https://github.com/Wei-Shaw/sub2api/blob/dece26153621959ccd7635814f4b2d08304e681a/frontend/src/components/common/DateRangePicker.vue#L121)、[当前 main 的相同实现](https://github.com/Wei-Shaw/sub2api/blob/0eee80ba87aa4f3aff7282bf1695aa6dc0471c36/frontend/src/components/common/DateRangePicker.vue#L121)。近 24 小时先减固定 86,400,000 毫秒再转换日期，夏令时附近不能一概替换成“今天减一个日历日”。

## 服务端实际完成什么

`GetUserBreakdown` 调用 `parseTimeRange`。后者只按 `2006-01-02` 解析日期，并在结束日期零点上加 24 小时，使该日期也被包含。查询不读取 `start_time/end_time` 或滚动 `24h` 参数。将 RFC3339 时间直接塞进 `start_date/end_date` 会解析失败并采用默认范围，不能用于获得精确时间结果。

例如北京时间 11 月 6 日 15:30，网页版“近 24 小时”发送 `start_date=2026-11-05&end_date=2026-11-06&timezone=Asia/Shanghai`；后端范围是 `[11-05 00:00, 11-07 00:00)`，而不是 `[11-05 15:30, 11-06 15:30)`。因此选择网页同款口径时，应在 UI 展示实际日期范围，并说明该选项按日期统计。

来源：[v0.2.19 日期解析](https://github.com/Wei-Shaw/sub2api/blob/dece26153621959ccd7635814f4b2d08304e681a/backend/internal/handler/admin/dashboard_handler.go#L34)、[v0.2.19 用户排行处理](https://github.com/Wei-Shaw/sub2api/blob/dece26153621959ccd7635814f4b2d08304e681a/backend/internal/handler/admin/dashboard_handler.go#L671)、[当前 main 日期解析](https://github.com/Wei-Shaw/sub2api/blob/0eee80ba87aa4f3aff7282bf1695aa6dc0471c36/backend/internal/handler/admin/dashboard_handler.go#L34)。

`DashboardService.GetUserBreakdownStats` 直接调用仓库方法。仓库对 `usage_logs` 执行时间筛选、`SUM/COUNT`、`GROUP BY ul.user_id`、服务端 `ORDER BY` 和 `LIMIT`。可排序字段包括 `total_tokens`、`input_tokens`、`output_tokens`、`cache_tokens`、`requests`、`cost`、`actual_cost`。处理器默认 50 条，允许 1～200 条。

这条路径是在服务端聚合，并不意味着八个预设各自维护一份事先计算好的榜单；已检查的仓库方法直接查 `usage_logs`，该处理器未包裹排行结果缓存。单次聚合请求仍会受服务端数据量和数据库性能影响，但省去了移动端下载全部明细及本地聚合的开销。

来源：[服务层](https://github.com/Wei-Shaw/sub2api/blob/dece26153621959ccd7635814f4b2d08304e681a/backend/internal/service/dashboard_service.go#L411)、[v0.2.19 聚合 SQL](https://github.com/Wei-Shaw/sub2api/blob/dece26153621959ccd7635814f4b2d08304e681a/backend/internal/repository/usage_log_repo_trend.go#L620)、[当前 main 聚合 SQL](https://github.com/Wei-Shaw/sub2api/blob/0eee80ba87aa4f3aff7282bf1695aa6dc0471c36/backend/internal/repository/usage_log_repo_trend.go#L620)。

## 其他入口及建议

- 首页消费排行使用 `/admin/dashboard/users-ranking`。它固定按消费排序，处理器有 5 分钟缓存；日期解析仍相同，因此不能替代通用 Tokens/请求数排行，也不能提供精确滚动 24 小时。来源：[首页调用](https://github.com/Wei-Shaw/sub2api/blob/0eee80ba87aa4f3aff7282bf1695aa6dc0471c36/frontend/src/views/admin/DashboardView.vue#L724)、[缓存及排行处理器](https://github.com/Wei-Shaw/sub2api/blob/0eee80ba87aa4f3aff7282bf1695aa6dc0471c36/backend/internal/handler/admin/dashboard_handler.go#L527)、[固定消费排序 SQL](https://github.com/Wei-Shaw/sub2api/blob/0eee80ba87aa4f3aff7282bf1695aa6dc0471c36/backend/internal/repository/usage_log_repo_trend.go#L146)。
- `/admin/dashboard/snapshot-v2` 批量返回概览和趋势，同样调用日期解析，未提供另一种精确 24 小时用户排行。处理器中的 RFC3339 `start/end` 接收逻辑属于 `POST /aggregation/backfill`，用于触发聚合回填，不是只读排行查询。来源：[snapshot-v2](https://github.com/Wei-Shaw/sub2api/blob/0eee80ba87aa4f3aff7282bf1695aa6dc0471c36/backend/internal/handler/admin/dashboard_snapshot_v2_handler.go#L73)、[回填入口](https://github.com/Wei-Shaw/sub2api/blob/0eee80ba87aa4f3aff7282bf1695aa6dc0471c36/backend/internal/handler/admin/dashboard_handler.go#L144)、[已检查的管理端路由](https://github.com/Wei-Shaw/sub2api/blob/0eee80ba87aa4f3aff7282bf1695aa6dc0471c36/backend/internal/server/routes/admin.go#L283)。
- 推荐移动端提供八项下拉框，默认今天；全部使用 `user-breakdown` 服务端排序、一次返回 Top 50，移除自动分页扫描明细的路径。近 24 小时应明确标为网页日期口径并展示范围。若将来必须严格滚动 24 小时，应先给后端增加真正接受时间戳的聚合查询能力，不能把当前整日结果宣称为精确 24 小时。
