package com.example.ab.analysis.config

import com.example.ab.clickhouse.ClickHouseClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AnalysisClickHouseConfig {
  @Bean
  fun clickHouseClient(properties: AnalysisProperties): ClickHouseClient =
    ClickHouseClient(
      properties.clickhouseUrl,
      properties.clickhouseUser,
      properties.clickhousePassword,
    )
}
