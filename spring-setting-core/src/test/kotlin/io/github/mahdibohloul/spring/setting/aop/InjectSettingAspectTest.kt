package io.github.mahdibohloul.spring.setting.aop

import io.github.mahdibohloul.spring.setting.services.SettingService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory
import reactor.core.publisher.Mono
import reactor.kotlin.test.test

class InjectSettingAspectTest {

  @InjectMocks
  private lateinit var service: SettingAspectTest.TestService

  @Mock
  private lateinit var settingService: SettingService

  private lateinit var injectSettingAspect: InjectSettingAspect

  @BeforeEach
  fun init() {
    MockitoAnnotations.openMocks(this)
    injectSettingAspect = InjectSettingAspect(settingService)
    val factory = AspectJProxyFactory(service)
    factory.addAspect(injectSettingAspect)
    service = factory.getProxy()
  }

  @Test
  fun `should inject test setting to the context of chain with success`() {
    // given

    // when
    whenever(settingService.loadSetting(SettingAspectTest.TestSetting::class))
      .thenReturn(Mono.just(SettingAspectTest.TestSetting()))

    // verify
    service.testMethod()
      .test()
      .verifyComplete()

    verify(settingService, times(1)).loadSetting(SettingAspectTest.TestSetting::class)
  }

  @Test
  fun `should inject test setting as an argument to the function`() {
    // given

    // when
    whenever(settingService.loadSetting(SettingAspectTest.TestSetting::class))
      .thenReturn(Mono.just(SettingAspectTest.TestSetting()))

    // verify
    service.testMethodArgs()
      .test()
      .verifyComplete()

    verify(settingService, times(1)).loadSetting(SettingAspectTest.TestSetting::class)
  }

  @Test
  fun `should inject test setting as an argument to the function with flux return type`() {
    // given

    // when
    whenever(settingService.loadSetting(SettingAspectTest.TestSetting::class))
      .thenReturn(Mono.just(SettingAspectTest.TestSetting()))

    // verify
    service.fluxMethodArgs()
      .test()
      .expectNext(SettingAspectTest.TestSetting())
      .verifyComplete()

    verify(settingService, times(1)).loadSetting(SettingAspectTest.TestSetting::class)
  }

  @Test
  fun `should inject test setting to the context of chain with flux return type`() {
    // given

    // when
    whenever(settingService.loadSetting(SettingAspectTest.TestSetting::class))
      .thenReturn(Mono.just(SettingAspectTest.TestSetting()))

    // verify
    service.fluxMethod()
      .test()
      .expectNext(SettingAspectTest.TestSetting())
      .verifyComplete()

    verify(settingService, times(1)).loadSetting(SettingAspectTest.TestSetting::class)
  }
}
