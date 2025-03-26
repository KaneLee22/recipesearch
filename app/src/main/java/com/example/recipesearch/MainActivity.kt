package com.example.recipesearch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ---------------------- 1. 数据模型 ----------------------
data class MealResponse(
    val meals: List<Meal>?
)

data class Meal(
    val idMeal: String?,
    val strMeal: String?,
    val strMealThumb: String?,
    val strInstructions: String?
)

// ---------------------- 2. Retrofit接口&实例 ----------------------
interface MealApiService {
    @GET("search.php")
    suspend fun searchMeals(
        @Query("s") query: String
    ): MealResponse
}

object RetrofitInstance {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://www.themealdb.com/api/json/v1/1/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val apiService: MealApiService by lazy {
        retrofit.create(MealApiService::class.java)
    }
}

// ---------------------- 3. UI状态 ----------------------
sealed class UiState {
    object Empty : UiState()
    object Loading : UiState()
    data class Success(val meals: List<Meal>) : UiState()
    data class Error(val message: String) : UiState()
}

// ---------------------- 4. ViewModel ----------------------
class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Empty)
    val uiState: StateFlow<UiState> = _uiState

    fun searchMeals(query: String) {
        if (query.isBlank()) {
            _uiState.value = UiState.Empty
            return
        }

        _uiState.value = UiState.Loading

        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitInstance.apiService.searchMeals(query)
                }

                val result = response.meals ?: emptyList()
                if (result.isNotEmpty()) {
                    _uiState.value = UiState.Success(result)
                } else {
                    _uiState.value = UiState.Error("No meals found for \"$query\"")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }
}

// ---------------------- 5. Compose UI ----------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search for a recipe") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                viewModel.searchMeals(searchQuery.text)
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Search")
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (uiState) {
            is UiState.Empty -> {
                Text("Please enter a keyword and click Search.")
            }
            is UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Success -> {
                val meals = (uiState as UiState.Success).meals
                MealList(meals = meals)
            }
            is UiState.Error -> {
                val errorMsg = (uiState as UiState.Error).message
                Text(
                    text = "Error: $errorMsg",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun MealList(meals: List<Meal>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(meals) { meal ->
            MealItem(meal)
        }
    }
}

@Composable
fun MealItem(meal: Meal) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            AsyncImage(
                model = meal.strMealThumb,
                contentDescription = meal.strMeal,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = meal.strMeal ?: "Unknown Meal",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = meal.strInstructions?.take(100) ?: "No instructions",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// ---------------------- 6. Activity ----------------------
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                // 传递ViewModel实例到MainScreen
                MainScreen(viewModel = viewModel)
            }
        }
    }
}