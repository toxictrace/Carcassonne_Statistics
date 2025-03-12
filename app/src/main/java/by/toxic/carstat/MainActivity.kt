package by.toxic.carstat

import android.animation.ValueAnimator
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import by.toxic.carstat.databinding.ActivityMainBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var navController: NavController? = null
    private var isEditing = false
    private lateinit var viewModel: GameViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Инициализация ViewModel
        viewModel = ViewModelProvider(this).get(GameViewModel::class.java)

        val isDarkTheme = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        Log.d("MainActivity", "Current theme: ${if (isDarkTheme) "Dark" else "Light"}")

        // Установка начального случайного фона
        setRandomBackground(isDarkTheme)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
        if (navHostFragment == null) {
            Log.e("MainActivity", "NavHostFragment is null!")
        } else if (navHostFragment !is NavHostFragment) {
            Log.e("MainActivity", "Fragment is not a NavHostFragment!")
        } else {
            Log.d("MainActivity", "NavHostFragment found")
            try {
                navController = navHostFragment.navController
                navController?.let { navCtrl ->
                    Log.d("MainActivity", "NavController initialized")

                    // Установка фона при смене фрагмента
                    navCtrl.addOnDestinationChangedListener { _, destination, _ ->
                        isEditing = when (destination.id) {
                            R.id.editPlayerFragment, R.id.editGameFragment -> true
                            else -> false
                        }
                        Log.d("MainActivity", "Editing mode: $isEditing, Destination: ${destination.label}")

                        // Обновление фона при смене фрагмента
                        setRandomBackground(isDarkTheme)

                        // Обновление размера иконок с анимацией
                        updateNavBarIconSizes(destination.id)
                    }

                    // Настройка нажатий на элементы NavBar
                    setupNavBarClicks(navCtrl)

                    // Настройка обработки кнопки "Назад"
                    setupBackPressedHandler(navCtrl)
                } ?: Log.e("MainActivity", "NavController is null!")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error initializing NavController: ${e.message}")
            }
        }
    }

    private fun setRandomBackground(isDarkTheme: Boolean) {
        val lightBackgrounds = listOf(
            R.drawable.light_background1,
            R.drawable.light_background2,
            R.drawable.light_background3,
            R.drawable.light_background4,
            R.drawable.light_background5,
            R.drawable.light_background6
        )

        val darkBackgrounds = listOf(
            R.drawable.dark_background1,
            R.drawable.dark_background2,
            R.drawable.dark_background3,
            R.drawable.dark_background4,
            R.drawable.dark_background5,
            R.drawable.dark_background6
        )

        // Выбираем случайный фон в зависимости от темы
        val selectedBackground = if (isDarkTheme) {
            darkBackgrounds[Random.nextInt(darkBackgrounds.size)]
        } else {
            lightBackgrounds[Random.nextInt(lightBackgrounds.size)]
        }

        // Проверяем, существует ли ресурс
        try {
            val options = RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE) // Кэшируем оригинальное изображение
                .skipMemoryCache(false) // Используем кэш памяти

            // Загружаем изображение с помощью Glide
            Glide.with(this)
                .load(selectedBackground)
                .apply(options)
                .into(binding.backgroundImage)

            Log.d("MainActivity", "Set background: $selectedBackground")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to load background: ${e.message}")
            // Устанавливаем запасной фон (например, прозрачный или цвет)
            binding.backgroundImage.setImageDrawable(null)
            binding.root.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
        }
    }

    private fun setupNavBarClicks(navController: NavController) {
        // Games
        binding.navItemGames.setOnClickListener {
            val isNavigationBlocked = navController.currentDestination?.id == R.id.playerProfileFragment ||
                    navController.currentDestination?.id == R.id.viewGameFragment
            if (isNavigationBlocked) {
                Log.d("MainActivity", "Navigation blocked: on PlayerProfileFragment or ViewGameFragment")
            } else if (!isEditing) {
                navController.navigate(R.id.gamesFragment)
            } else {
                Log.d("MainActivity", "Navigation blocked due to editing mode")
            }
        }

        // Players
        binding.navItemPlayers.setOnClickListener {
            val isNavigationBlocked = navController.currentDestination?.id == R.id.playerProfileFragment ||
                    navController.currentDestination?.id == R.id.viewGameFragment
            if (isNavigationBlocked) {
                Log.d("MainActivity", "Navigation blocked: on PlayerProfileFragment or ViewGameFragment")
            } else if (!isEditing) {
                navController.navigate(R.id.playersFragment)
            } else {
                Log.d("MainActivity", "Navigation blocked due to editing mode")
            }
        }

        // Add
        binding.navItemAdd.setOnClickListener {
            when (navController.currentDestination?.id) {
                R.id.gamesFragment -> {
                    lifecycleScope.launch {
                        viewModel.allPlayers.collectLatest { players ->
                            if (players.size < 2) {
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle(R.string.confirm_exit_title)
                                    .setMessage(R.string.minimum_players_to_start_game_error)
                                    .setPositiveButton(R.string.yes) { _, _ -> }
                                    .show()
                            } else {
                                Log.d("MainActivity", "Navigating to EditGameFragment from Games")
                                navController.navigate(R.id.action_gamesFragment_to_editGameFragment)
                            }
                        }
                    }
                }
                R.id.playersFragment -> {
                    Log.d("MainActivity", "Navigating to EditPlayerFragment from Players")
                    val bundle = Bundle().apply { putInt("playerId", -1) }
                    navController.navigate(R.id.action_playersFragment_to_editPlayerFragment, bundle)
                }
                R.id.editGameFragment -> {
                    Log.d("MainActivity", "Adding player in EditGameFragment")
                    val fragment = (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment)
                        .childFragmentManager.primaryNavigationFragment as? EditGameFragment
                    fragment?.addPlayerFromNavBar() ?: Log.e("MainActivity", "EditGameFragment not found in primary navigation")
                }
                R.id.viewGameFragment -> {
                    Log.d("MainActivity", "Navigating to EditGameFragment from ViewGameFragment")
                    val gameId = navController.currentBackStackEntry?.arguments?.getInt("gameId", -1) ?: -1
                    if (gameId != -1) {
                        val bundle = Bundle().apply { putInt("gameId", gameId) }
                        navController.navigate(R.id.action_viewGameFragment_to_editGameFragment, bundle)
                    } else {
                        Log.e("MainActivity", "Invalid gameId for editing")
                    }
                }
                else -> {
                    Log.d("MainActivity", "Add clicked, but no action defined for ${navController.currentDestination?.label}")
                }
            }
        }

        // Statistics
        binding.navItemStatistics.setOnClickListener {
            val isNavigationBlocked = navController.currentDestination?.id == R.id.playerProfileFragment ||
                    navController.currentDestination?.id == R.id.viewGameFragment
            if (isNavigationBlocked) {
                Log.d("MainActivity", "Navigation blocked: on PlayerProfileFragment or ViewGameFragment")
            } else if (!isEditing) {
                navController.navigate(R.id.statisticsFragment)
            } else {
                Log.d("MainActivity", "Navigation blocked due to editing mode")
            }
        }

        // Settings
        binding.navItemSettings.setOnClickListener {
            val isNavigationBlocked = navController.currentDestination?.id == R.id.playerProfileFragment ||
                    navController.currentDestination?.id == R.id.viewGameFragment
            if (isNavigationBlocked) {
                Log.d("MainActivity", "Navigation blocked: on PlayerProfileFragment or ViewGameFragment")
            } else if (!isEditing) {
                navController.navigate(R.id.settingsFragment)
            } else {
                Log.d("MainActivity", "Navigation blocked due to editing mode")
            }
        }
    }

    private fun updateNavBarIconSizes(destinationId: Int) {
        val normalSizeDp = 48
        val selectedSizeDp = 72

        // Сбрасываем размеры всех иконок до 48dp с анимацией
        animateIconSize(binding.navIconGames, normalSizeDp)
        animateIconSize(binding.navIconPlayers, normalSizeDp)
        animateIconSize(binding.navIconAdd, normalSizeDp)
        animateIconSize(binding.navIconStatistics, normalSizeDp)
        animateIconSize(binding.navIconSettings, normalSizeDp)

        // Увеличиваем размер выбранной иконки до 72dp с анимацией
        when (destinationId) {
            R.id.gamesFragment, R.id.viewGameFragment, R.id.editGameFragment -> {
                animateIconSize(binding.navIconGames, selectedSizeDp)
            }
            R.id.playersFragment, R.id.editPlayerFragment, R.id.playerProfileFragment -> {
                animateIconSize(binding.navIconPlayers, selectedSizeDp)
            }
            R.id.statisticsFragment -> {
                animateIconSize(binding.navIconStatistics, selectedSizeDp)
            }
            R.id.settingsFragment -> {
                animateIconSize(binding.navIconSettings, selectedSizeDp)
            }
        }
    }

    private fun animateIconSize(imageView: ImageView, targetSizeDp: Int) {
        val density = resources.displayMetrics.density
        val currentSizePx = imageView.layoutParams.width
        val targetSizePx = (targetSizeDp * density).toInt()

        val animator = ValueAnimator.ofInt(currentSizePx, targetSizePx)
        animator.duration = 200
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addUpdateListener { animation ->
            val value = animation.animatedValue as Int
            val layoutParams = imageView.layoutParams
            layoutParams.width = value
            layoutParams.height = value
            imageView.layoutParams = layoutParams
            imageView.requestLayout()
        }
        animator.start()
    }

    private fun setupBackPressedHandler(navController: NavController) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (navController.currentDestination?.id) {
                    R.id.gamesFragment, R.id.statisticsFragment, R.id.settingsFragment -> {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle(getString(R.string.exit_app_title))
                            .setMessage(getString(R.string.exit_app_message))
                            .setPositiveButton(getString(R.string.yes)) { _, _ -> finish() }
                            .setNegativeButton(getString(R.string.no), null)
                            .show()
                    }
                    R.id.editGameFragment -> {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle(getString(R.string.confirm_exit_title))
                            .setMessage(getString(R.string.confirm_discard_changes))
                            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                                navController.navigateUp()
                            }
                            .setNegativeButton(getString(R.string.no), null)
                            .show()
                    }
                    else -> {
                        navController.navigateUp()
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }
}