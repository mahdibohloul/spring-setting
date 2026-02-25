package io.github.mahdibohloul.spring.setting.demo

import io.github.mahdibohloul.spring.setting.Setting
import io.github.mahdibohloul.spring.setting.services.SettingService
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.stereotype.Component


@SpringBootApplication
class SpringSettingDemoApplication

fun main(args: Array<String>) {
  runApplication<SpringSettingDemoApplication>(*args)
}

data class TestSetting(
  val name: String,
  @Max(value = 100, message = "Age cannot be greater than 100")
  @Min(value = 0, message = "Age cannot be less than 0")
  val age: Int,
  val isActive: Boolean,
) : Setting

@Component
class TestRunner(
  private val settingService: SettingService,
) : CommandLineRunner {
  private val logger = LoggerFactory.getLogger(this::class.java)

  override fun run(vararg args: String) {
    val setting = TestSetting(name = "John Doe", age = 30, isActive = true)
    settingService.saveSetting(setting).block()
    logger.info("Saved setting: $setting")

    val invalidSetting = TestSetting(name = "Jane Doe", age = 150, isActive = false)
    settingService.saveSetting(invalidSetting).subscribe(
      { logger.info("Saved invalid setting: $invalidSetting") },
      { error -> logger.error("Error saving invalid setting: ${error.message}") }
    )
  }
}
