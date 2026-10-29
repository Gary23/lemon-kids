package com.lemonkids.parent.feature.profile

import androidx.lifecycle.viewModelScope
import com.lemonkids.shared.model.*
import com.lemonkids.shared.repository.*
import java.lang.reflect.Proxy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileLoadingTest {
    private val dispatcher = StandardTestDispatcher()
    private val views = mutableListOf<androidx.lifecycle.ViewModel>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { views.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }

    private fun profileTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try { block() } finally { views.forEach { it.viewModelScope.cancel() } }
    }

    private inline fun <reified T> repository(crossinline call: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            val result = call(method.name.substringBefore('-'))
            if (result is Result<*>) Result::class.java.getDeclaredMethod("unbox-impl").invoke(result) else result
        } as T

    private fun auth(user: User? = User(familyId = "family"), failure: Boolean = false) = repository<AuthRepository> {
        when (it) {
            "observeCurrentUser" -> flow { if (failure) error("auth unavailable"); emit(user) }
            "fetchChildUsers" -> Result.success(emptyList<ChildUserInfo>())
            else -> error("Unexpected $it")
        }
    }



    @Test fun taskListFailureCanRetryWithoutDuplicateSubscriptions() = profileTest {
        var fail = true
        var active = 0
        var maximum = 0
        val templates = repository<TaskTemplateRepository> {
            flow {
                if (fail) error("missing table")
                active++; maximum = maxOf(active, maximum)
                try { emit(listOf(TaskTemplate(id = "t", title = "任务"))); awaitCancellation() }
                finally { active-- }
            }
        }
        val vm = TaskTemplateManageViewModel(templates, auth()).also { views += it }
        runCurrent()
        assertFalse(vm.uiState.value.isLoading)
        assertNotNull(vm.uiState.value.listError)
        fail = false
        vm.retryTemplates(); runCurrent()
        assertNull(vm.uiState.value.listError)
        assertEquals("t", vm.uiState.value.templates.single().id)
        repeat(3) { vm.retryTemplates(); runCurrent() }
        assertEquals(1, active)
        assertEquals(1, maximum)
    }

    @Test fun categoryFailureIsIsolatedAndIncompleteAssignmentsCannotBeSaved() = profileTest {
        var fail = true
        var writes = 0
        val categories = repository<CategoryRepository> {
            when (it) {
                "observeCategories" -> flowOf(listOf(Category(id = "c", familyId = "family", name = "分类")))
                "observeCategoryTaskTemplates" -> flow<List<CategoryTaskTemplate>> {
                    if (fail) error("missing associations")
                    emit(emptyList())
                }
                "replaceCategoryTaskTemplates" -> { writes++; Result.success(Unit) }
                else -> error("Unexpected $it")
            }
        }
        val templates = repository<TaskTemplateRepository> { flowOf(listOf(TaskTemplate(id = "t"))) }
        val vm = CategoryManageViewModel(categories, templates, auth()).also { views += it }
        runCurrent()
        val state = vm.uiState.value
        assertEquals(1, state.categories.size)
        assertEquals(1, state.templates.size)
        assertFalse(state.isLoading)
        assertNotNull(state.assignmentsError)
        assertFalse(state.canConfigureTasks)
        vm.saveCategoryTasks(state.categories.single(), emptyList()); runCurrent()
        assertEquals(0, writes)
        fail = false
        vm.retryLoads(); runCurrent()
        assertTrue(vm.uiState.value.canConfigureTasks)
        vm.saveCategoryTasks(state.categories.single(), listOf("t")); runCurrent()
        assertEquals(1, writes)
    }

    @Test fun laterFailureKeepsPreviouslyLoadedData() = profileTest {
        val templates = repository<TaskTemplateRepository> {
            flow { emit(listOf(TaskTemplate(id = "cached"))); error("network lost") }
        }
        val vm = TaskTemplateManageViewModel(templates, auth()).also { views += it }
        runCurrent()
        assertEquals("cached", vm.uiState.value.templates.single().id)
        assertNotNull(vm.uiState.value.listError)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test fun missingFamilyAndAuthFailureStopLoading() = profileTest {
        val templates = repository<TaskTemplateRepository> { error("must not query") }
        for (source in listOf(auth(null), auth(failure = true))) {
            val vm = TaskTemplateManageViewModel(templates, source).also { views += it }
            runCurrent()
            assertFalse(vm.uiState.value.isLoading)
            assertNotNull(vm.uiState.value.listError)
        }
    }




    @Test fun deviceLogFailureCanRestartObservation() = profileTest {
        var fail = true
        val source = repository<DeviceStatusRepository> {
            flow<List<DeviceStatusLog>> { if (fail) error("logs unavailable"); emit(emptyList()) }
        }
        val baseAuth = auth()
        val withChild = object : AuthRepository by baseAuth {
            override suspend fun fetchChildUsers(familyId: String) = Result.success(listOf(ChildUserInfo("child", "孩子", 0)))
        }
        val vm = DeviceStatusLogViewModel(withChild, source).also { views += it }
        runCurrent()
        assertNotNull(vm.uiState.value.errorMessage)
        fail = false; vm.refresh(); runCurrent()
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test fun lifecycleCancellationIsNotReportedAsLoadFailure() = profileTest {
        var failure: Exception? = null
        val job = backgroundScope.launchProfileLoad(onError = { failure = it }) { awaitCancellation() }
        runCurrent(); job.cancel(); runCurrent()
        assertTrue(job.isCancelled)
        assertNull(failure)
    }

    @Test fun recycleBinFailureCanRecover() = profileTest {
        var fail = true
        val tasks = repository<TaskRepository> {
            flow<List<Task>> { if (fail) error("network"); emit(emptyList()) }
        }
        val vm = RecycleBinViewModel(tasks, auth()).also { views += it }
        runCurrent()
        assertNotNull(vm.uiState.value.loadError)
        assertFalse(vm.uiState.value.isLoading)
        fail = false; vm.loadDeletedTasks(); runCurrent()
        assertNull(vm.uiState.value.loadError)
        assertFalse(vm.uiState.value.isLoading)
    }
}
