package cm.stevru.andropose.adapters

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import cm.stevru.andropose.fragments.ImageFragment
import cm.stevru.andropose.fragments.VideoFragment

/* Adapter class for tab view elements*/
class FragmentAdapter (
    private val context: Context,
    fragmentManager: FragmentManager
): FragmentStatePagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    private val tabsTitle = arrayOf("Image", "Video")
    // Implements methods
    override fun getCount(): Int {
        return tabsTitle.size
    }

    override fun getItem(position: Int): Fragment {
        return when(position){
            0 ->{// img frag
                ImageFragment()
            }
            1 ->{// vid frag
                VideoFragment()
            }

            else -> getItem(position)
        }
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return tabsTitle[position]
    }
}