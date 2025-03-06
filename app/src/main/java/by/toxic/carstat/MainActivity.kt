package by.toxic.carstat

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
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
                    binding.bottomNavigation.setupWithNavController(navCtrl)
                    Log.d("MainActivity", "NavController initialized")

                    navCtrl.addOnDestinationChangedListener { _, destination, _ ->
                        isEditing = when (destination.id) {
                            R.id.editPlayerFragment, R.id.editGameFragment -> true
                            else -> false
                        }
                        Log.d("MainActivity", "Editing mode: $isEditing, Destination: ${destination.label}")
                    }

                    binding.bottomNavigation.setOnItemSelectedListener { item ->
                        when (item.itemId) {
                            R.id.addFragment -> {
                                when (navCtrl.currentDestination?.id) {
                                    R.id.gamesFragment -> {
                                        Log.d("MainActivity", "Navigating to EditGameFragment from Games")
                                        navCtrl.navigate(R.id.action_gamesFragment_to_editGameFragment)
                                        false
                                    }
                                    R.id.playersFragment -> {
                                        Log.d("MainActivity", "Navigating to EditPlayerFragment from Players")
                                        val bundle = Bundle().apply { putInt("playerId", -1) }
                                        navCtrl.navigate(R.id.action_playersFragment_to_editPlayerFragment, bundle)
                                        false
                                    }
                                    R.id.editGameFragment -> {
                                        Log.d("MainActivity", "Adding player in EditGameFragment")
                                        val fragment = navHostFragment.childFragmentManager.primaryNavigationFragment as? EditGameFragment
                                        fragment?.addPlayerFromNavBar() ?: Log.e("MainActivity", "EditGameFragment not found in primary navigation")
                                        false
                                    }
                                    R.id.viewGameFragment -> {
                                        Log.d("MainActivity", "Navigating to EditGameFragment from ViewGameFragment")
                                        val gameId = navCtrl.currentBackStackEntry?.arguments?.getInt("gameId", -1) ?: -1
                                        if (gameId != -1) {
                                            val bundle = Bundle().apply { putInt("gameId", gameId) }
                                            navCtrl.navigate(R.id.action_viewGameFragment_to_editGameFragment, bundle)
                                        } else {
                                            Log.e("MainActivity", "Invalid gameId for editing")
                                        }
                                        false
                                    }
                                    else -> {
                                        Log.d("MainActivity", "Add clicked, but no action defined for ${navCtrl.currentDestination?.label}")
                                        false
                                    }
                                }
                            }
                            else -> {
                                if (!isEditing) {
                                    navCtrl.navigate(item.itemId)
                                    true
                                } else {
                                    Log.d("MainActivity", "Navigation blocked due to editing mode")
                                    false
                                }
                            }
                        }
                    }
                } ?: Log.e("MainActivity", "NavController is null!")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error initializing NavController: ${e.message}")
            }
        }
    }
}