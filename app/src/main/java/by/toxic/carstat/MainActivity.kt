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
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import by.toxic.carstat.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var navController: NavController? = null
    private var isEditing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val isDarkTheme = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        Log.d("MainActivity", "Current theme: ${if (isDarkTheme) "Dark" else "Light"}")

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

                    // Инициализация размера текущей иконки с анимацией
                    navCtrl.addOnDestinationChangedListener { _, destination, _ ->
                        isEditing = when (destination.id) {
                            R.id.editPlayerFragment, R.id.editGameFragment -> true
                            else -> false
                        }
                        Log.d("MainActivity", "Editing mode: $isEditing, Destination: ${destination.label}")

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

    private fun setupNavBarClicks(navController: NavController) {
        // Games
        binding.navItemGames.setOnClickListener {
            if (!isEditing) {
                navController.navigate(R.id.gamesFragment)
            } else {
                Log.d("MainActivity", "Navigation blocked due to editing mode")
            }
        }

        // Players
        binding.navItemPlayers.setOnClickListener {
            if (!isEditing) {
                navController.navigate(R.id.playersFragment)
            } else {
                Log.d("MainActivity", "Navigation blocked due to editing mode")
            }
        }

        // Add
        binding.navItemAdd.setOnClickListener {
            when (navController.currentDestination?.id) {
                R.id.gamesFragment -> {
                    Log.d("MainActivity", "Navigating to EditGameFragment from Games")
                    navController.navigate(R.id.action_gamesFragment_to_editGameFragment)
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
            if (!isEditing) {
                navController.navigate(R.id.statisticsFragment)
            } else {
                Log.d("MainActivity", "Navigation blocked due to editing mode")
            }
        }

        // Settings
        binding.navItemSettings.setOnClickListener {
            if (!isEditing) {
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
            R.id.playersFragment, R.id.editPlayerFragment -> {
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
        animator.duration = 200 // Длительность анимации в миллисекундах (0.2 секунды)
        animator.interpolator = AccelerateDecelerateInterpolator() // Плавное ускорение и замедление
        animator.addUpdateListener { animation ->
            val value = animation.animatedValue as Int
            val layoutParams = imageView.layoutParams
            layoutParams.width = value
            layoutParams.height = value
            imageView.layoutParams = layoutParams
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