package by.toxic.carstat

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import by.toxic.carstat.databinding.ActivityMainBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var navController: NavController? = null
    private var isEditing = false
    private lateinit var viewModel: GameViewModel
    private var isCustomBackgroundsEnabled = false
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data: Intent? = result.data
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            task.addOnSuccessListener { account ->
                Log.d("MainActivity", "Google Sign-In successful: ${account.displayName}")
                navController?.currentDestination?.let { destination ->
                    if (destination.id == R.id.settingsFragment) {
                        val fragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                            ?.childFragmentManager?.primaryNavigationFragment as? SettingsFragment
                        fragment?.handleSignInResult(task)
                    }
                }
            }.addOnFailureListener { e ->
                Log.e("MainActivity", "Google Sign-In failed: ${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this).get(GameViewModel::class.java)

        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        val theme = sharedPref.getString("theme", "Follow System")
        isCustomBackgroundsEnabled = sharedPref.getBoolean("background_enabled", false)
        applyTheme(theme)

        val isDarkTheme = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        Log.d("MainActivity", "Current theme: ${if (isDarkTheme) "Dark" else "Light"}")

        setRandomBackground(isDarkTheme, isCustomBackgroundsEnabled)

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
                    navCtrl.addOnDestinationChangedListener { _, destination, _ ->
                        isEditing = when (destination.id) {
                            R.id.editPlayerFragment, R.id.editGameFragment -> true
                            else -> false
                        }
                        Log.d("MainActivity", "Editing mode: $isEditing, Destination: ${destination.label}")
                        setRandomBackground(isDarkTheme, isCustomBackgroundsEnabled)
                        updateNavBarIconSizes(destination.id)
                    }
                    setupNavBarClicks(navCtrl)
                    setupBackPressedHandler(navCtrl)
                } ?: Log.e("MainActivity", "NavController is null!")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error initializing NavController: ${e.message}")
            }
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    fun applyTheme(theme: String?) {
        when (theme) {
            "Light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "Dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "Follow System" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        recreateUI()
    }

    private fun recreateUI() {
        val isDarkTheme = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        setRandomBackground(isDarkTheme, isCustomBackgroundsEnabled)
        navController?.currentDestination?.let { updateNavBarIconSizes(it.id) }
    }

    fun enableCustomBackgrounds(enabled: Boolean) {
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        sharedPref.edit().putBoolean("background_enabled", enabled).apply()
        isCustomBackgroundsEnabled = enabled
        val isDarkTheme = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        setRandomBackground(isDarkTheme, enabled)
    }

    private fun setRandomBackground(isDarkTheme: Boolean, useCustomBackgrounds: Boolean) {
        if (!useCustomBackgrounds) {
            binding.backgroundImage.setImageDrawable(null)
            // Используем цвет background из colors.xml, Android сам выберет правильный в зависимости от темы
            val backgroundColor = ContextCompat.getColor(this, R.color.background)
            binding.root.setBackgroundColor(backgroundColor)
            return
        }

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

        val selectedBackground = if (isDarkTheme) {
            darkBackgrounds[Random.nextInt(darkBackgrounds.size)]
        } else {
            lightBackgrounds[Random.nextInt(lightBackgrounds.size)]
        }

        try {
            val options = RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .skipMemoryCache(false)

            Glide.with(this)
                .load(selectedBackground)
                .apply(options)
                .into(binding.backgroundImage)

            Log.d("MainActivity", "Set background: $selectedBackground")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to load background: ${e.message}")
            binding.backgroundImage.setImageDrawable(null)
            binding.root.setBackgroundColor(ContextCompat.getColor(this, R.color.background)) // Используем R.color.background как запасной
        }
    }

    fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    private fun setupNavBarClicks(navController: NavController) {
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
                    fragment?.addPlayerFromNavBar() ?: Log.e("MainActivity", "EditGameFragment not found")
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

        val density = resources.displayMetrics.density
        val normalSizePx = (normalSizeDp * density).toInt()
        val selectedSizePx = (selectedSizeDp * density).toInt()

        binding.navIconGames.clearAnimation()
        binding.navIconPlayers.clearAnimation()
        binding.navIconAdd.clearAnimation()
        binding.navIconStatistics.clearAnimation()
        binding.navIconSettings.clearAnimation()

        setIconSize(binding.navIconGames, normalSizePx)
        setIconSize(binding.navIconPlayers, normalSizePx)
        setIconSize(binding.navIconAdd, normalSizePx)
        setIconSize(binding.navIconStatistics, normalSizePx)
        setIconSize(binding.navIconSettings, normalSizePx)

        when (destinationId) {
            R.id.gamesFragment, R.id.viewGameFragment, R.id.editGameFragment -> {
                animateIconSize(binding.navIconGames, selectedSizePx)
            }
            R.id.playersFragment, R.id.editPlayerFragment, R.id.playerProfileFragment -> {
                animateIconSize(binding.navIconPlayers, selectedSizePx)
            }
            R.id.statisticsFragment -> {
                animateIconSize(binding.navIconStatistics, selectedSizePx)
            }
            R.id.settingsFragment -> {
                animateIconSize(binding.navIconSettings, selectedSizePx)
            }
        }
    }

    private fun setIconSize(imageView: ImageView, sizePx: Int) {
        val layoutParams = imageView.layoutParams
        layoutParams.width = sizePx
        layoutParams.height = sizePx
        imageView.layoutParams = layoutParams
        imageView.requestLayout()
    }

    private fun animateIconSize(imageView: ImageView, targetSizePx: Int) {
        val currentSizePx = imageView.layoutParams.width
        if (currentSizePx == targetSizePx) return

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
                            .setTitle(R.string.exit_app_title)
                            .setMessage(R.string.exit_app_message)
                            .setPositiveButton(R.string.yes) { _, _ -> finish() }
                            .setNegativeButton(R.string.no) { _, _ -> }
                            .setOnDismissListener { navController.navigateUp() }
                            .show()
                    }
                    R.id.editGameFragment -> {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle(R.string.confirm_exit_title)
                            .setMessage(R.string.confirm_discard_changes)
                            .setPositiveButton(R.string.yes) { _, _ ->
                                navController.navigateUp()
                            }
                            .setNegativeButton(R.string.no) { _, _ -> }
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