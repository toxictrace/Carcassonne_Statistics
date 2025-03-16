package by.toxic.carstat

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import by.toxic.carstat.databinding.ActivityMainBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var navController: NavController? = null
    private var isEditing = false
    private lateinit var viewModel: GameViewModel
    private var isCustomBackgroundsEnabled = false
    private var lastSelectedIcon: ImageView? = null
    private var isAddButtonVisible = true
    private var isEditButtonMode = false
    private var isHidingAddButton = false
    private var isAnimating = false
    private var areNavIconsVisible = true
    private var currentBackgroundId: Int? = null
    lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001
    private var hasStoragePermission = false
    private var permissionDialog: AlertDialog? = null

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

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        Log.d("MainActivity", "Permission result: $isGranted")
        hasStoragePermission = isGranted
        if (isGranted) {
            Log.d("MainActivity", "Storage permission granted")
            permissionDialog?.dismiss()
            initializeApp()
        } else {
            Log.d("MainActivity", "Storage permission denied")
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d("MainActivity", "onCreate: Checking storage permission")
        checkAndRequestStorageAccess()
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume: Re-checking storage permission")
        checkAndRequestStorageAccess()
    }

    private fun checkAndRequestStorageAccess() {
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        val isFirstRun = sharedPref.getBoolean("is_first_run", true)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            hasStoragePermission = Environment.isExternalStorageManager()
            Log.d("MainActivity", "Android 11+: isExternalStorageManager = $hasStoragePermission")
            if (!hasStoragePermission) {
                if (isFirstRun && permissionDialog == null) {
                    showPermissionRationaleDialog()
                } else if (permissionDialog == null) {
                    showPermissionDeniedDialog()
                }
            } else {
                if (isFirstRun) sharedPref.edit().putBoolean("is_first_run", false).apply()
                permissionDialog?.dismiss()
                initializeApp()
            }
        } else {
            val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            hasStoragePermission = permission == PackageManager.PERMISSION_GRANTED
            Log.d("MainActivity", "Android < 11: WRITE_EXTERNAL_STORAGE granted = $hasStoragePermission")
            if (!hasStoragePermission) {
                if (isFirstRun || shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    if (permissionDialog == null) showPermissionRationaleDialog()
                } else {
                    permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            } else {
                if (isFirstRun) sharedPref.edit().putBoolean("is_first_run", false).apply()
                permissionDialog?.dismiss()
                initializeApp()
            }
        }
    }

    fun requestStorageAccess() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = android.net.Uri.fromParts("package", packageName, null)
            startActivity(intent)
        } else {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun showPermissionRationaleDialog() {
        Log.d("MainActivity", "Showing permission rationale dialog")
        val message = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            getString(R.string.storage_permission_message) + "\n\n" +
                    "On MIUI: Go to Settings > Apps > Carcassonne Statistics > Permissions > Allow access to all files."
        } else {
            getString(R.string.storage_permission_message)
        }
        permissionDialog = AlertDialog.Builder(this)
            .setTitle(R.string.storage_permission_title)
            .setMessage(message)
            .setPositiveButton(R.string.ok) { _, _ ->
                Log.d("MainActivity", "User clicked OK in rationale dialog")
                requestStorageAccess()
            }
            .setNegativeButton(R.string.cancel_button) { dialog, _ ->
                Log.d("MainActivity", "User canceled permission request")
                dialog.dismiss()
                permissionDialog = null
                initializeApp()
            }
            .setCancelable(false)
            .create()
        permissionDialog?.show()
    }

    private fun showPermissionDeniedDialog() {
        Log.d("MainActivity", "Showing permission denied dialog")
        val message = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            getString(R.string.permission_denied_message) + "\n\n" +
                    "On MIUI: Go to Settings > Apps > Carcassonne Statistics > Permissions > Allow access to all files."
        } else {
            getString(R.string.permission_denied_message)
        }
        permissionDialog = AlertDialog.Builder(this)
            .setTitle(R.string.permission_denied_title)
            .setMessage(message)
            .setPositiveButton(R.string.go_to_settings) { _, _ ->
                Log.d("MainActivity", "User clicked Go to Settings")
                requestStorageAccess()
            }
            .setNegativeButton(R.string.cancel_button) { dialog, _ ->
                Log.d("MainActivity", "User canceled denied dialog")
                dialog.dismiss()
                permissionDialog = null
                initializeApp()
            }
            .setCancelable(false)
            .create()
        permissionDialog?.show()
    }

    private fun initializeApp() {
        if (::viewModel.isInitialized) {
            Log.d("MainActivity", "App already initialized, skipping")
            return
        }

        Log.d("MainActivity", "initializeApp: Starting app initialization with storage permission = $hasStoragePermission")
        viewModel = ViewModelProvider(this).get(GameViewModel::class.java)

        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        val theme = sharedPref.getString("theme", getString(R.string.theme_follow_system))
        isCustomBackgroundsEnabled = sharedPref.getBoolean("background_enabled", false)
        applyTheme(theme)

        val isDarkTheme = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        Log.d("MainActivity", "Current theme: ${if (isDarkTheme) "Dark" else "Light"}")

        setRandomBackground(isDarkTheme, isCustomBackgroundsEnabled)

        val normalSizeDp = 48
        val density = resources.displayMetrics.density
        val normalSizePx = (normalSizeDp * density).toInt()
        setIconSize(binding.navIconGames, normalSizePx)
        setIconSize(binding.navIconPlayers, normalSizePx)
        setIconSize(binding.navIconAdd, normalSizePx)
        setIconSize(binding.navIconStatistics, normalSizePx)
        setIconSize(binding.navIconSettings, normalSizePx)

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
                        if (!isAnimating) updateNavBarIconAndAnimation(destination.id)
                    }
                    setupNavBarClicks(navCtrl)
                    setupBackPressedHandler(navCtrl)
                    navCtrl.currentDestination?.id?.let { updateNavBarIconAndAnimation(it) }
                } ?: Log.e("MainActivity", "NavController is null!")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error initializing NavController: ${e.message}")
            }
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        if (!hasStoragePermission) {
            Toast.makeText(this, "Some features may be limited without storage permission", Toast.LENGTH_LONG).show()
        }
    }

    fun hasStoragePermission(): Boolean {
        return hasStoragePermission
    }

    fun applyTheme(theme: String?) {
        when (theme) {
            getString(R.string.theme_light) -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            getString(R.string.theme_dark) -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            getString(R.string.theme_follow_system) -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        recreateUI()
    }

    private fun recreateUI() {
        val isDarkTheme = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        setRandomBackground(isDarkTheme, isCustomBackgroundsEnabled)
        navController?.currentDestination?.let { if (!isAnimating) updateNavBarIconAndAnimation(it.id) }
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
            val backgroundColor = ContextCompat.getColor(this, R.color.background)
            binding.root.setBackgroundColor(backgroundColor)
            currentBackgroundId = null
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

        val backgrounds = if (isDarkTheme) darkBackgrounds else lightBackgrounds
        val selectedBackground = currentBackgroundId ?: backgrounds[Random.nextInt(backgrounds.size)].also { currentBackgroundId = it }

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
            binding.root.setBackgroundColor(ContextCompat.getColor(this, R.color.background))
            currentBackgroundId = null
        }
    }

    fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    fun signOutFromGoogle() {
        googleSignInClient.signOut().addOnCompleteListener {
            Log.d("MainActivity", "Sign out from Google completed")
            navController?.currentDestination?.let { destination ->
                if (destination.id == R.id.settingsFragment) {
                    val fragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                        ?.childFragmentManager?.primaryNavigationFragment as? SettingsFragment
                    fragment?.handleSignInResult(null)
                }
            }
        }
    }

    fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun setupNavBarClicks(navController: NavController) {
        val navOptions = NavOptions.Builder()
            .setEnterAnim(R.anim.fade_in)
            .setExitAnim(R.anim.fade_out)
            .setPopEnterAnim(R.anim.fade_in)
            .setPopExitAnim(R.anim.fade_out)
            .build()

        val isDarkTheme = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        binding.navItemGames.setOnClickListener {
            val isNavigationBlocked = navController.currentDestination?.id == R.id.playerProfileFragment ||
                    navController.currentDestination?.id == R.id.viewGameFragment
            if (isNavigationBlocked) {
                Log.d("MainActivity", "Navigation blocked: on PlayerProfileFragment or ViewGameFragment")
            } else if (!isEditing) {
                setRandomBackground(isDarkTheme, isCustomBackgroundsEnabled)
                navController.navigate(R.id.gamesFragment, null, navOptions)
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
                setRandomBackground (isDarkTheme, isCustomBackgroundsEnabled)
                navController.navigate(R.id.playersFragment, null, navOptions)
            } else {
                Log.d("MainActivity", "Navigation blocked due to editing mode")
            }
        }

        binding.navItemAdd.setOnClickListener {
            when (navController.currentDestination?.id) {
                R.id.gamesFragment -> {
                    lifecycleScope.launch {
                        viewModel.allPlayers.collectLatest { players ->
                            if (navController.currentDestination?.id == R.id.gamesFragment) {
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
                R.id.playerProfileFragment -> {
                    Log.d("MainActivity", "Navigating to EditPlayerFragment from PlayerProfileFragment")
                    val playerId = navController.currentBackStackEntry?.arguments?.getInt("playerId", -1) ?: -1
                    if (playerId != -1) {
                        val bundle = Bundle().apply { putInt("playerId", playerId) }
                        navController.navigate(R.id.action_playerProfileFragment_to_editPlayerFragment, bundle)
                    } else {
                        Log.e("MainActivity", "Invalid playerId for editing")
                    }
                }
                R.id.settingsFragment -> {
                    Log.d("MainActivity", "Add/Edit clicked on SettingsFragment, no action defined")
                }
                else -> {
                    Log.d("MainActivity", "Add/Edit clicked, but no action defined for ${navController.currentDestination?.label}")
                }
            }
        }

        binding.navItemStatistics.setOnClickListener {
            val isNavigationBlocked = navController.currentDestination?.id == R.id.playerProfileFragment ||
                    navController.currentDestination?.id == R.id.viewGameFragment
            if (isNavigationBlocked) {
                Log.d("MainActivity", "Navigation blocked: on PlayerProfileFragment or ViewGameFragment")
            } else if (!isEditing) {
                setRandomBackground(isDarkTheme, isCustomBackgroundsEnabled)
                navController.navigate(R.id.statisticsFragment, null, navOptions)
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
                setRandomBackground(isDarkTheme, isCustomBackgroundsEnabled)
                navController.navigate(R.id.settingsFragment, null, navOptions)
            } else {
                Log.d("MainActivity", "Navigation blocked due to editing mode")
            }
        }
    }

    private fun updateNavBarIconAndAnimation(destinationId: Int) {
        Log.d("MainActivity", "Updating Navbar for destination: $destinationId, isAnimating: $isAnimating")
        if (isAnimating) {
            Log.d("MainActivity", "Animation in progress, skipping update")
            return
        }

        val normalSizeDp = 48
        val selectedSizeDp = 72
        val density = resources.displayMetrics.density
        val normalSizePx = (normalSizeDp * density).toInt()
        val selectedSizePx = (selectedSizeDp * density).toInt()

        val previousIcon = lastSelectedIcon
        Log.d("MainActivity", "Previous selected icon: $previousIcon")

        binding.navIconGames.clearAnimation()
        binding.navIconPlayers.clearAnimation()
        binding.navIconAdd.clearAnimation()
        binding.navIconStatistics.clearAnimation()
        binding.navIconSettings.clearAnimation()

        if (isAddButtonVisible && !isHidingAddButton) {
            setIconSize(binding.navIconAdd, normalSizePx)
            Log.d("MainActivity", "Set Add button size to normal: $normalSizePx")
        }

        when (destinationId) {
            R.id.gamesFragment, R.id.viewGameFragment, R.id.editGameFragment -> {
                if (previousIcon != null && previousIcon != binding.navIconGames && previousIcon != binding.navIconAdd) {
                    Log.d("MainActivity", "Animating shrink for previous icon: $previousIcon, current size: ${previousIcon.layoutParams.width}")
                    animateIconSize(previousIcon, normalSizePx)
                }
                Log.d("MainActivity", "Games icon current size: ${binding.navIconGames.layoutParams.width}, expected: $selectedSizePx")
                if (binding.navIconGames.layoutParams.width != selectedSizePx) {
                    Log.d("MainActivity", "Animating grow for Games icon")
                    animateIconSize(binding.navIconGames, selectedSizePx)
                } else {
                    Log.d("MainActivity", "Games icon already at selected size, skipping animation")
                }
                lastSelectedIcon = binding.navIconGames
            }
            R.id.playersFragment, R.id.editPlayerFragment, R.id.playerProfileFragment -> {
                if (previousIcon != null && previousIcon != binding.navIconPlayers && previousIcon != binding.navIconAdd) {
                    Log.d("MainActivity", "Animating shrink for previous icon: $previousIcon, current size: ${previousIcon.layoutParams.width}")
                    animateIconSize(previousIcon, normalSizePx)
                }
                Log.d("MainActivity", "Players icon current size: ${binding.navIconPlayers.layoutParams.width}, expected: $selectedSizePx")
                if (binding.navIconPlayers.layoutParams.width != selectedSizePx) {
                    Log.d("MainActivity", "Animating grow for Players icon")
                    animateIconSize(binding.navIconPlayers, selectedSizePx)
                } else {
                    Log.d("MainActivity", "Players icon already at selected size, skipping animation")
                }
                lastSelectedIcon = binding.navIconPlayers
            }
            R.id.statisticsFragment -> {
                if (previousIcon != null && previousIcon != binding.navIconStatistics && previousIcon != binding.navIconAdd) {
                    Log.d("MainActivity", "Animating shrink for previous icon: $previousIcon, current size: ${previousIcon.layoutParams.width}")
                    animateIconSize(previousIcon, normalSizePx)
                }
                Log.d("MainActivity", "Statistics icon current size: ${binding.navIconStatistics.layoutParams.width}, expected: $selectedSizePx")
                if (binding.navIconStatistics.layoutParams.width != selectedSizePx) {
                    Log.d("MainActivity", "Animating grow for Statistics icon")
                    animateIconSize(binding.navIconStatistics, selectedSizePx)
                } else {
                    Log.d("MainActivity", "Statistics icon already at selected size, skipping animation")
                }
                lastSelectedIcon = binding.navIconStatistics
            }
            R.id.settingsFragment -> {
                if (previousIcon != null && previousIcon != binding.navIconSettings && previousIcon != binding.navIconAdd) {
                    Log.d("MainActivity", "Animating shrink for previous icon: $previousIcon, current size: ${previousIcon.layoutParams.width}")
                    animateIconSize(previousIcon, normalSizePx)
                }
                Log.d("MainActivity", "Settings icon current size: ${binding.navIconSettings.layoutParams.width}, expected: $selectedSizePx")
                if (binding.navIconSettings.layoutParams.width != selectedSizePx) {
                    Log.d("MainActivity", "Animating grow for Settings icon")
                    animateIconSize(binding.navIconSettings, selectedSizePx)
                } else {
                    Log.d("MainActivity", "Settings icon already at selected size, skipping animation")
                }
                lastSelectedIcon = binding.navIconSettings
            }
        }

        when (destinationId) {
            R.id.gamesFragment, R.id.playersFragment -> {
                Log.d("MainActivity", "Showing all icons and Add button for destination: $destinationId")
                if (!areNavIconsVisible) {
                    showNavBarIcons(normalSizePx)
                }
                if (!isAddButtonVisible) {
                    showAddButton(normalSizePx)
                } else if (isEditButtonMode) {
                    animateReplaceEditWithAdd(normalSizePx)
                }
                when (destinationId) {
                    R.id.gamesFragment -> {
                        if (binding.navIconGames.layoutParams.width != selectedSizePx) {
                            Log.d("MainActivity", "Forcing grow for Games icon after show with slow animation")
                            animateIconSize(binding.navIconGames, selectedSizePx, 600)
                        }
                    }
                    R.id.playersFragment -> {
                        if (binding.navIconPlayers.layoutParams.width != selectedSizePx) {
                            Log.d("MainActivity", "Forcing grow for Players icon after show with slow animation")
                            animateIconSize(binding.navIconPlayers, selectedSizePx, 600)
                        }
                    }
                }
            }
            R.id.statisticsFragment, R.id.settingsFragment -> {
                Log.d("MainActivity", "Showing all icons, hiding Add button for destination: $destinationId")
                if (!areNavIconsVisible) {
                    showNavBarIcons(normalSizePx)
                }
                if (isAddButtonVisible) {
                    hideAddButton()
                }
            }
            R.id.editGameFragment -> {
                Log.d("MainActivity", "Hiding all icons except Add for EditGameFragment")
                if (areNavIconsVisible) {
                    hideNavBarIcons()
                }
                if (!isAddButtonVisible) {
                    showAddButton(normalSizePx)
                } else if (isEditButtonMode) {
                    animateReplaceEditWithAdd(normalSizePx)
                }
            }
            R.id.viewGameFragment, R.id.playerProfileFragment -> {
                Log.d("MainActivity", "Hiding all icons except Edit for destination: $destinationId")
                if (areNavIconsVisible) {
                    hideNavBarIcons()
                }
                if (isAddButtonVisible && !isEditButtonMode) {
                    animateReplaceAddWithEdit(normalSizePx)
                } else if (!isAddButtonVisible) {
                    showEditButton(normalSizePx)
                }
            }
            R.id.editPlayerFragment -> {
                Log.d("MainActivity", "Hiding all icons for EditPlayerFragment")
                if (areNavIconsVisible) {
                    hideNavBarIcons()
                }
                if (isAddButtonVisible) {
                    hideAddButton()
                }
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

    private fun animateIconSize(imageView: ImageView, targetSizePx: Int, duration: Long = 400) {
        val currentSizePx = imageView.layoutParams.width
        if (currentSizePx == targetSizePx) return

        isAnimating = true
        val animator = ValueAnimator.ofInt(currentSizePx, targetSizePx)
        animator.duration = duration
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addUpdateListener { animation ->
            val value = animation.animatedValue as Int
            val layoutParams = imageView.layoutParams
            layoutParams.width = value
            layoutParams.height = value
            imageView.layoutParams = layoutParams
            imageView.requestLayout()
        }
        animator.addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) {
                Log.d("MainActivity", "Animation started for icon: $imageView with duration: $duration")
            }
            override fun onAnimationEnd(animation: android.animation.Animator) {
                isAnimating = false
                Log.d("MainActivity", "Animation ended for icon: $imageView")
            }
            override fun onAnimationCancel(animation: android.animation.Animator) {
                isAnimating = false
                Log.d("MainActivity", "Animation canceled for icon: $imageView")
            }
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })
        animator.start()
    }

    private fun animateReplaceAddWithEdit(targetSizePx: Int) {
        Log.d("MainActivity", "Animating replace Add with Edit")
        val addIcon = binding.navIconAdd
        addIcon.clearAnimation()
        setIconSize(addIcon, targetSizePx)
        Log.d("MainActivity", "Add icon size before animation: ${addIcon.layoutParams.width}x${addIcon.layoutParams.height}")

        isAnimating = true
        val animation = AnimationUtils.loadAnimation(this, R.anim.scale_up_and_shrink_to_dot)
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {
                Log.d("MainActivity", "Scale up and shrink animation started for Add to Edit")
            }
            override fun onAnimationEnd(animation: Animation?) {
                Log.d("MainActivity", "Scale up and shrink animation ended, replacing with Edit")
                addIcon.setImageResource(R.drawable.button_edit)
                addIcon.contentDescription = getString(R.string.edit_player)
                isEditButtonMode = true
                setIconSize(addIcon, targetSizePx)
                Log.d("MainActivity", "Add icon size after shrink: ${addIcon.layoutParams.width}x${addIcon.layoutParams.height}")
                val growAnimation = AnimationUtils.loadAnimation(this@MainActivity, R.anim.grow_from_dot)
                growAnimation.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {
                        Log.d("MainActivity", "Grow animation started for Edit button")
                    }
                    override fun onAnimationEnd(animation: Animation?) {
                        Log.d("MainActivity", "Grow animation ended for Edit button")
                        setIconSize(addIcon, targetSizePx)
                        Log.d("MainActivity", "Add icon size after grow: ${addIcon.layoutParams.width}x${addIcon.layoutParams.height}")
                        isAnimating = false
                    }
                    override fun onAnimationRepeat(animation: Animation?) {}
                })
                addIcon.startAnimation(growAnimation)
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })
        addIcon.startAnimation(animation)
    }

    private fun animateReplaceEditWithAdd(targetSizePx: Int) {
        Log.d("MainActivity", "Animating replace Edit with Add")
        val addIcon = binding.navIconAdd
        addIcon.clearAnimation()
        setIconSize(addIcon, targetSizePx)
        Log.d("MainActivity", "Edit icon size before animation: ${addIcon.layoutParams.width}x${addIcon.layoutParams.height}")

        isAnimating = true
        val animation = AnimationUtils.loadAnimation(this, R.anim.scale_up_and_shrink_to_dot)
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {
                Log.d("MainActivity", "Scale up and shrink animation started for Edit to Add")
            }
            override fun onAnimationEnd(animation: Animation?) {
                Log.d("MainActivity", "Scale up and shrink animation ended, replacing with Add")
                addIcon.setImageResource(R.drawable.ic_add_icon)
                addIcon.contentDescription = getString(R.string.add_player)
                isEditButtonMode = false
                setIconSize(addIcon, targetSizePx)
                Log.d("MainActivity", "Edit icon size after shrink: ${addIcon.layoutParams.width}x${addIcon.layoutParams.height}")
                val growAnimation = AnimationUtils.loadAnimation(this@MainActivity, R.anim.grow_from_dot)
                growAnimation.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {
                        Log.d("MainActivity", "Grow animation started for Add button")
                    }
                    override fun onAnimationEnd(animation: Animation?) {
                        Log.d("MainActivity", "Grow animation ended for Add button")
                        setIconSize(addIcon, targetSizePx)
                        Log.d("MainActivity", "Edit icon size after grow: ${addIcon.layoutParams.width}x${addIcon.layoutParams.height}")
                        isAnimating = false
                    }
                    override fun onAnimationRepeat(animation: Animation?) {}
                })
                addIcon.startAnimation(growAnimation)
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })
        addIcon.startAnimation(animation)
    }

    private fun showAddButton(targetSizePx: Int) {
        Log.d("MainActivity", "Showing Add button")
        val addIcon = binding.navIconAdd
        if (!isAddButtonVisible) {
            addIcon.visibility = View.VISIBLE
            addIcon.setImageResource(R.drawable.ic_add_icon)
            addIcon.contentDescription = getString(R.string.add_player)
            isEditButtonMode = false
            isAddButtonVisible = true
            setIconSize(addIcon, targetSizePx)
            Log.d("MainActivity", "Add icon size before grow: ${addIcon.layoutParams.width}x${addIcon.layoutParams.height}")
            isAnimating = true
            val growAnimation = AnimationUtils.loadAnimation(this, R.anim.grow_from_dot)
            growAnimation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {
                    Log.d("MainActivity", "Grow animation started for Add button")
                }
                override fun onAnimationEnd(animation: Animation?) {
                    Log.d("MainActivity", "Grow animation ended for Add button")
                    setIconSize(addIcon, targetSizePx)
                    Log.d("MainActivity", "Add icon size after grow: ${addIcon.layoutParams.width}x${addIcon.layoutParams.height}")
                    isAnimating = false
                }
                override fun onAnimationRepeat(animation: Animation?) {}
            })
            addIcon.startAnimation(growAnimation)
        }
    }

    private fun showEditButton(targetSizePx: Int) {
        Log.d("MainActivity", "Showing Edit button")
        val addIcon = binding.navIconAdd
        if (!isAddButtonVisible) {
            addIcon.visibility = View.VISIBLE
            addIcon.setImageResource(R.drawable.button_edit)
            addIcon.contentDescription = getString(R.string.edit_player)
            isAddButtonVisible = true
            isEditButtonMode = true
            setIconSize(addIcon, targetSizePx)
            Log.d("MainActivity", "Edit icon size before grow: ${addIcon.layoutParams.width}x${addIcon.layoutParams.height}")
            isAnimating = true
            val growAnimation = AnimationUtils.loadAnimation(this, R.anim.grow_from_dot)
            growAnimation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {
                    Log.d("MainActivity", "Grow animation started for Edit button")
                }
                override fun onAnimationEnd(animation: Animation?) {
                    Log.d("MainActivity", "Grow animation ended for Edit button")
                    setIconSize(addIcon, targetSizePx)
                    Log.d("MainActivity", "Edit icon size after grow: ${addIcon.layoutParams.width}x${addIcon.layoutParams.height}")
                    isAnimating = false
                }
                override fun onAnimationRepeat(animation: Animation?) {}
            })
            addIcon.startAnimation(growAnimation)
        }
    }

    private fun hideAddButton() {
        Log.d("MainActivity", "Hiding Add button, isAddButtonVisible: $isAddButtonVisible, isHidingAddButton: $isHidingAddButton")
        val addIcon = binding.navIconAdd
        if (isAddButtonVisible && !isHidingAddButton) {
            isHidingAddButton = true
            isAnimating = true
            addIcon.clearAnimation()
            val animation = AnimationUtils.loadAnimation(this, R.anim.shrink_to_dot)
            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {
                    Log.d("MainActivity", "Shrink animation started for Add button")
                }
                override fun onAnimationEnd(animation: Animation?) {
                    Log.d("MainActivity", "Shrink animation ended for Add button")
                    addIcon.visibility = View.GONE
                    isAddButtonVisible = false
                    isEditButtonMode = false
                    isHidingAddButton = false
                    isAnimating = false
                }
                override fun onAnimationRepeat(animation: Animation?) {}
            })
            addIcon.startAnimation(animation)
        }
    }

    private fun hideNavBarIcons() {
        Log.d("MainActivity", "Hiding NavBar icons")
        if (!areNavIconsVisible) return

        val icons = listOf(binding.navIconGames, binding.navIconPlayers, binding.navIconStatistics, binding.navIconSettings)
        isAnimating = true
        var completedAnimations = 0

        for (icon in icons) {
            if (icon.visibility == View.VISIBLE) {
                icon.clearAnimation()
                val animation = AnimationUtils.loadAnimation(this, R.anim.shrink_to_dot)
                animation.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {
                        Log.d("MainActivity", "Shrink animation started for icon: $icon")
                    }
                    override fun onAnimationEnd(animation: Animation?) {
                        Log.d("MainActivity", "Shrink animation ended for icon: $icon")
                        icon.visibility = View.GONE
                        setIconSize(icon, 0)
                        completedAnimations++
                        if (completedAnimations == icons.size) {
                            areNavIconsVisible = false
                            isAnimating = false
                        }
                    }
                    override fun onAnimationRepeat(animation: Animation?) {}
                })
                icon.startAnimation(animation)
            } else {
                completedAnimations++
                if (completedAnimations == icons.size) {
                    areNavIconsVisible = false
                    isAnimating = false
                }
            }
        }
    }

    private fun showNavBarIcons(targetSizePx: Int) {
        Log.d("MainActivity", "Showing NavBar icons")
        if (areNavIconsVisible) return

        val icons = listOf(binding.navIconGames, binding.navIconPlayers, binding.navIconStatistics, binding.navIconSettings)
        isAnimating = true
        var completedAnimations = 0

        for (icon in icons) {
            val targetSize = if (icon == lastSelectedIcon) {
                val selectedSizePx = (72 * resources.displayMetrics.density).toInt()
                selectedSizePx
            } else {
                targetSizePx
            }

            if (icon.visibility != View.VISIBLE || icon.layoutParams.width != targetSize) {
                icon.visibility = View.VISIBLE
                setIconSize(icon, 0)
                val growAnimation = AnimationUtils.loadAnimation(this, R.anim.grow_from_dot)
                growAnimation.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {
                        Log.d("MainActivity", "Grow animation started for icon: $icon")
                    }
                    override fun onAnimationEnd(animation: Animation?) {
                        Log.d("MainActivity", "Grow animation ended for icon: $icon")
                        setIconSize(icon, targetSize)
                        completedAnimations++
                        if (completedAnimations == icons.size) {
                            areNavIconsVisible = true
                            isAnimating = false
                        }
                    }
                    override fun onAnimationRepeat(animation: Animation?) {}
                })
                icon.startAnimation(growAnimation)
            } else {
                completedAnimations++
                if (completedAnimations == icons.size) {
                    areNavIconsVisible = true
                    isAnimating = false
                }
            }
        }
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