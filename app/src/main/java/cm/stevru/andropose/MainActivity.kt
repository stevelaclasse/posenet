package cm.stevru.andropose

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.ViewPager
import cm.stevru.andropose.adapters.FragmentAdapter
import cm.stevru.andropose.fragments.ImageFragment
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity(){

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // init views
        initViews()

        // add listener
        addTabListener()
    }

    private fun initViews(){
        tabLayout = findViewById(R.id.tabLayout_id)
        viewPager = findViewById(R.id.viewPager_id)
        tabLayout.tabGravity = TabLayout.GRAVITY_FILL

        //init adapter
        val adapter = FragmentAdapter(this, supportFragmentManager)
        viewPager.adapter = adapter

        tabLayout.setupWithViewPager(viewPager) // link tab to pager
    }

    private fun addTabListener(){
        viewPager.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabLayout))
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener{

            override fun onTabSelected(p0: TabLayout.Tab) {
                viewPager.currentItem = p0.position

            }
            override fun onTabReselected(p0: TabLayout.Tab) {}
            override fun onTabUnselected(p0: TabLayout.Tab?) {}
        })
    }
}
