package com.example.recipesearchapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// data models -- added strArea + strCategory to make a "description" for assignment requirements
data class Meal(
    @Json(name = "idMeal") val idMeal: String,
    @Json(name = "strMeal") val name: String,
    @Json(name = "strCategory") val category: String?,
    @Json(name = "strArea") val area: String?,
    @Json(name = "strMealThumb") val thumbnail: String
)

data class MealsResponse(
    @Json(name = "meals") val meals: List<Meal>?
)

// retrofit api service
interface MealApi {
    @GET("search.php")
    suspend fun searchMeals(@Query("s") query: String): MealsResponse
}

// data access layer responsible for fetching recipe data
class RemoteSource(private val api: MealApi) {
    suspend fun searchRecipes(query: String): Result<List<Meal>> {
        return try {
            val response = api.searchMeals(query)
            val meals = response.meals ?: emptyList()
            if (meals.isNotEmpty()) {
                Result.success(meals)
            } else {
                Result.failure(Exception("No recipes found."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// ui state
sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val recipes: List<Meal>) : UiState()
    data class Error(val message: String) : UiState()
}

// view model
class SearchViewModel : androidx.lifecycle.ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder().build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://www.themealdb.com/api/json/v1/1/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val api: MealApi = retrofit.create(MealApi::class.java)
    private val remoteSource = RemoteSource(api)

    fun search(query: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val result = remoteSource.searchRecipes(query)
            _uiState.value = result.fold(
                onSuccess = { recipes -> UiState.Success(recipes) },
                onFailure = { error -> UiState.Error(error.message ?: "An error occurred while fetching recipes.") }
            )
        }
    }
}

// main activity & compose ui
class MainActivity : ComponentActivity() {
    private val searchViewModel: SearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                SearchScreen(viewModel = searchViewModel)
            }
        }
    }
}

@Composable
fun SearchScreen(viewModel: SearchViewModel) {
    val state by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search Recipe") },
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { viewModel.search(query) }) {
                Text("Search")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        when (state) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(text = (state as UiState.Error).message)
                }
            }
            is UiState.Success -> {
                val recipes = (state as UiState.Success).recipes
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(recipes) { meal ->
                        RecipeCard(meal = meal)
                    }
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Enter a search query to see recipes.")
                }
            }
        }
    }
}

@Composable
fun RecipeCard(meal: Meal) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            AsyncImage(
                model = meal.thumbnail,
                contentDescription = meal.name,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = meal.name, style = MaterialTheme.typography.titleMedium)
                val description = listOfNotNull(meal.area, meal.category).joinToString(" - ")
                if (description.isNotBlank()) {
                    Text(text = description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}