package com.ownid.demo.ui.activity

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.annotation.CallSuper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.MenuCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.ownid.demo.R
import com.ownid.demo.databinding.ActivityMainBinding

abstract class BaseMainActivity : AppCompatActivity() {

    abstract val serverUrl: String
    abstract fun getLoginFragment(): Fragment
    abstract fun getCreateFragment(): Fragment

    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.vpActivityMain.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> getLoginFragment()
                1 -> getCreateFragment()
                else -> throw IllegalStateException("Wrong position: $position")
            }
        }

        TabLayoutMediator(binding.tlActivityMain, binding.vpActivityMain) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.log_in)
                1 -> getString(R.string.create_account)
                else -> throw IllegalStateException("Wrong position: $position")
            }
        }.attach()

        // Fix for https://issuetracker.google.com/u/0/issues/143095219
        ViewPager2ViewHeightAnimator().viewPager2 = binding.vpActivityMain

        binding.tvActivityMainServer.text = serverUrl

        binding.ivActivityMainVendorLogo.setOnClickListener {
            runCatching {
                PopupMenu(this, binding.ivActivityMainVendorLogo).apply {
                    menu.add(10, 1000, 1000, "Show logs")
                    MenuCompat.setGroupDividerEnabled(menu, true)

                    setOnMenuItemClickListener { menuItem: MenuItem ->
                        onMenuClicked(menuItem)
                        true
                    }
                    show()
                }
            }
        }
    }

    @CallSuper
    open fun onMenuClicked(menuItem: MenuItem) {
        if (menuItem.itemId == 1000) {
            AlertDialog.Builder(this@BaseMainActivity).apply {
                setView(AppCompatTextView(this@BaseMainActivity).apply {
                    text = (this@BaseMainActivity.application as BaseDemoApp).logs.toString()
                    setTextIsSelectable(true)
                    setHorizontallyScrolling(true)
                    isVerticalScrollBarEnabled = true
                })
            }.show()
        }
    }

    fun isBusy(isBusy: Boolean) {
        binding.vActivityMainPbLock.isVisible = isBusy
        binding.pbActivityMain.isVisible = isBusy
    }

    fun showError(throwable: Throwable?) {
        val message = throwable?.cause?.message ?: throwable?.message ?: "Unknown error"
        showError(message)
    }

    fun showError(message: String) {
        runOnUiThread {
            Log.e(this.javaClass.simpleName, "showError: $message")

            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).apply {
                setBackgroundTint(context.getColor(R.color.ownid_error))
            }.show()
        }
    }

    class ViewPager2ViewHeightAnimator {
        var viewPager2: ViewPager2? = null
            set(value) {
                if (field != value) {
                    field?.unregisterOnPageChangeCallback(onPageChangeCallback)
                    field = value
                    value?.registerOnPageChangeCallback(onPageChangeCallback)
                }
            }

        private val layoutManager: LinearLayoutManager?
            get() = (viewPager2?.getChildAt(0) as? RecyclerView)?.layoutManager as? LinearLayoutManager

        private val onPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels)
                recalculate(position, positionOffset)
            }
        }

        private fun recalculate(position: Int, positionOffset: Float = 0f) = layoutManager?.apply {
            val leftView = findViewByPosition(position) ?: return@apply
            val rightView = findViewByPosition(position + 1)
            viewPager2?.apply {
                val leftHeight = getMeasuredViewHeightFor(leftView)
                layoutParams = layoutParams.apply {
                    height = if (rightView != null) {
                        val rightHeight = getMeasuredViewHeightFor(rightView)
                        leftHeight + ((rightHeight - leftHeight) * positionOffset).toInt()
                    } else {
                        leftHeight
                    }
                }
                invalidate()
            }
        }

        private fun getMeasuredViewHeightFor(view: View): Int {
            val wMeasureSpec = View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY)
            val hMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            view.measure(wMeasureSpec, hMeasureSpec)
            return view.measuredHeight
        }
    }
}