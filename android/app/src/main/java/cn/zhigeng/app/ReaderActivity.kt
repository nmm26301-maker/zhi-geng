@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
package cn.zhigeng.app

import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.navigator.epub.*
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.*
import org.readium.r2.shared.publication.*
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File

/** Readium EPUB navigator and offline TXT adapter, with a quiet, native reader shell. */
class ReaderActivity : FragmentActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var root: FrameLayout
    private lateinit var frame: FrameLayout
    private lateinit var header: LinearLayout
    private lateinit var footer: LinearLayout
    private lateinit var caption: TextView
    private lateinit var progress: TextView
    private lateinit var seek: SeekBar
    private lateinit var progressLabel: TextView
    private var publication: Publication? = null
    private var navigator: EpubNavigatorFragment? = null
    private var positions: List<Locator> = emptyList()
    private var current: Locator? = null
    private var ready = false
    private var chrome = false
    private var menu: Dialog? = null
    private var key = ""
    private var bookTitle = ""
    private var fontSize = 1.18
    private var leading = 1.8
    private var margins = 1.25
    private var paragraph = .65
    private var theme = "paper"
    private var serif = true
    private var scrolling = false
    private var original = false
    private val paper get() = when(theme){"night"->0xff202622.toInt();"white"->0xfffdfcf9.toInt();else->0xfff7f3e9.toInt()}
    private val ink get() = if(theme=="night") 0xffd6d9ce.toInt() else 0xff303b34.toInt()
    private val muted get() = if(theme=="night") 0xffa3ae9f.toInt() else 0xff7d8579.toInt()
    private val forest get() = if(theme=="night") 0xffb4c8a8.toInt() else 0xff244e40.toInt()
    private val surface get() = if(theme=="night") 0xff2a332c.toInt() else 0xfffffdf8.toInt()
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun shape(color:Int,radius:Int=18)=GradientDrawable().apply { setColor(color);cornerRadius=dp(radius).toFloat() }
    private fun col()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    private fun row()=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
    private fun label(value:String,size:Float=14f,color:Int=ink)=TextView(this).apply { text=value;textSize=size;setTextColor(color);includeFontPadding=false }
    private fun action(value:String,description:String=value,call:()->Unit)=label(value,14f,forest).apply { gravity=Gravity.CENTER;minHeight=dp(48);setPadding(dp(12),dp(8),dp(12),dp(8));background=shape(if(theme=="night") 0xff354234.toInt() else 0xffe9eee4.toInt());contentDescription=description;setOnClickListener { call() } }
    private fun LinearLayout.add(view:View,top:Int=0){addView(view,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(top) })}
    override fun onCreate(state:Bundle?){
        // Publication resources are reopened from the local URI; locators restore process/rotation state.
        super.onCreate(null)
        prefs=getSharedPreferences("reader",MODE_PRIVATE)
        key=intent.getStringExtra("uri").orEmpty();bookTitle=intent.getStringExtra("title")?:"本地图书"
        fontSize=prefs.getFloat("readium.font",1.18f).toDouble();leading=prefs.getFloat("readium.line",1.8f).toDouble();margins=prefs.getFloat("readium.margin",1.25f).toDouble();paragraph=prefs.getFloat("readium.paragraph",.65f).toDouble();theme=prefs.getString("readium.theme","paper")?:"paper";serif=prefs.getBoolean("readium.serif",true);scrolling=prefs.getBoolean("readium.scroll",false);original=prefs.getBoolean("readium.original",false)
        root=FrameLayout(this);root.setBackgroundColor(paper);setContentView(root)
        frame=FrameLayout(this).apply { id=R.id.reader_container;setPadding(0,dp(38),0,dp(30)) };root.addView(frame,FrameLayout.LayoutParams(-1,-1))
        caption=label(bookTitle,11f,muted).apply { gravity=Gravity.CENTER;maxLines=1;setPadding(dp(28),0,dp(28),0) };root.addView(caption,FrameLayout.LayoutParams(-1,dp(30),Gravity.TOP).apply { topMargin=dp(6) })
        progress=label("正在整理书页…",11f,muted).apply { gravity=Gravity.CENTER };root.addView(progress,FrameLayout.LayoutParams(-1,dp(28),Gravity.BOTTOM))
        buildChrome();immersive();openBook()
    }
    private fun immersive(){window.statusBarColor=paper;window.navigationBarColor=paper;window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY}
    override fun onWindowFocusChanged(hasFocus:Boolean){super.onWindowFocusChanged(hasFocus);if(hasFocus)immersive()}
    private fun preferences()=EpubPreferences(backgroundColor=org.readium.r2.navigator.preferences.Color(paper),textColor=org.readium.r2.navigator.preferences.Color(ink),theme=if(theme=="night")Theme.DARK else Theme.LIGHT,fontFamily=if(serif)FontFamily("Zhigeng Serif") else FontFamily.SANS_SERIF,fontSize=fontSize,lineHeight=leading,pageMargins=margins,paragraphSpacing=paragraph,paragraphIndent=2.0,publisherStyles=original,scroll=scrolling,columnCount=ColumnCount.ONE,textAlign=TextAlign.JUSTIFY,hyphens=false)
    private fun buildChrome(){
        header=col().apply { setPadding(dp(16),dp(30),dp(16),dp(16));setBackgroundColor(surface) }
        val top=row();top.addView(action("‹","返回书架"){finish()},LinearLayout.LayoutParams(dp(48),dp(48)))
        val title=col().apply { setPadding(dp(16),0,dp(8),0) };title.add(label(bookTitle,16f).apply { maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END });title.add(label("知更 · 阅读",10f,muted),6);top.addView(title,LinearLayout.LayoutParams(0,-2,1f));top.addView(action("＋","添加书签"){bookmark()},LinearLayout.LayoutParams(dp(48),dp(48)));header.add(top)
        root.addView(header,FrameLayout.LayoutParams(-1,-2,Gravity.TOP))
        footer=col().apply { setPadding(dp(24),dp(20),dp(24),dp(28));background=shape(surface,26) }
        progressLabel=label("阅读进度",12f,muted);footer.add(progressLabel)
        seek=SeekBar(this).apply { max=1000;progressTintList=ColorStateList.valueOf(forest);thumbTintList=ColorStateList.valueOf(forest);contentDescription="阅读进度";setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onProgressChanged(v:SeekBar?,p:Int,user:Boolean){if(user)progressLabel.text="跳转到 ${(p/10f).toInt()}%"};override fun onStartTrackingTouch(v:SeekBar?){};override fun onStopTrackingTouch(v:SeekBar?){if(positions.isNotEmpty())navigator?.go(positions[((positions.size-1)*(v?.progress?:0)/1000.0).toInt().coerceIn(0,positions.lastIndex)])}}) };footer.add(seek,8)
        val turns=row();turns.addView(action("‹ 上一页"){navigator?.goBackward(true)},LinearLayout.LayoutParams(0,dp(44),1f));turns.addView(action("下一页 ›"){navigator?.goForward(true)},LinearLayout.LayoutParams(0,dp(44),1f).apply { leftMargin=dp(12) });footer.add(turns,8)
        val tools=row();listOf("目录" to {toc()},"书签" to {bookmarks()},"排版" to {settings()},"收起" to {showChrome(false)}).forEach { (text,run)->tools.addView(action(text,call=run),LinearLayout.LayoutParams(0,dp(48),1f).apply { if(tools.childCount>0)leftMargin=dp(8) }) };footer.add(tools,16)
        root.addView(footer,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));showChrome(false)
    }
    private fun showChrome(show:Boolean){chrome=show;header.visibility=if(show)View.VISIBLE else View.GONE;footer.visibility=if(show)View.VISIBLE else View.GONE;updateProgress()}
    private fun openBook(){
        val loading=label("把时间，留给阅读。\n\n正在打开本地图书…",16f,muted).apply { gravity=Gravity.CENTER;setLineSpacing(dp(8).toFloat(),1f) };frame.addView(loading,FrameLayout.LayoutParams(-1,-1))
        lifecycleScope.launch {
            try {
                val opened=withContext(Dispatchers.IO){
                    require(key.startsWith("content://")||key.startsWith("file://")){"请从书架打开本地图书"}
                    val url=if(intent.getStringExtra("format")=="txt"){
                        val converted=contentResolver.openInputStream(Uri.parse(key))!!.use { TxtPublication.convert(TxtPublication.read(it),bookTitle,File(cacheDir,"text-publications")) };Uri.fromFile(converted).toAbsoluteUrl()!!
                    }else Uri.parse(key).toAbsoluteUrl()!!
                    val http=DefaultHttpClient();val retriever=AssetRetriever(contentResolver,http);val asset=retriever.retrieve(url).getOrElse { throw IllegalStateException("文件无法读取，请重新导入") }
                    val opened=PublicationOpener(DefaultPublicationParser(this@ReaderActivity,http,retriever,null)).open(asset,allowUserInteraction=false).getOrElse { throw IllegalStateException("无法解析 EPUB，请检查文件是否完整或带有 DRM") }
                    if(opened.isRestricted){opened.close();throw IllegalStateException("这本 EPUB 带有 DRM，当前阅读器无法打开")}
                    opened to opened.positions()
                }
                publication=opened.first;positions=opened.second
                val initial=runCatching { Locator.fromJSON(JSONObject(prefs.getString("$key:locator","")!!)) }.getOrNull()
                val factory=EpubNavigatorFactory(opened.first).createFragmentFactory(initialLocator=initial,initialPreferences=preferences(),listener=object:EpubNavigatorFragment.Listener{override fun onExternalLinkActivated(url:AbsoluteUrl){val value=url.toString();if(value.startsWith("https://"))startActivity(Intent(this@ReaderActivity,InAppBrowserActivity::class.java).putExtra("url",value))}},configuration=EpubNavigatorFragment.Configuration {
                    servedAssets=listOf("fonts/.*");addFontFamilyDeclaration(FontFamily("Zhigeng Serif"),alternates=listOf(FontFamily.SERIF)){ addFontFace { addSource("fonts/zhigeng-serif.woff2") } }
                })
                supportFragmentManager.fragmentFactory=factory
                navigator=factory.instantiate(classLoader,EpubNavigatorFragment::class.java.name) as EpubNavigatorFragment
                frame.removeView(loading);supportFragmentManager.beginTransaction().replace(R.id.reader_container,navigator!!).commitNow()
                navigator!!.addInputListener(object:InputListener{override fun onTap(event:TapEvent):Boolean{val w=navigator?.view?.width?:1;when{chrome->showChrome(false);event.point.x<w*.22->navigator?.goBackward(true);event.point.x>w*.78->navigator?.goForward(true);else->showChrome(true)};return true}})
                lifecycleScope.launch { navigator!!.currentLocator.collect { current=it;ready=true;updateProgress();prefs.edit().putString("$key:locator",it.toJSON().toString()).apply() } }
                if(!prefs.getBoolean("readium.tip",false)){Toast.makeText(this@ReaderActivity,"轻点屏幕中央，唤出阅读菜单",Toast.LENGTH_LONG).show();prefs.edit().putBoolean("readium.tip",true).apply()}
                if(initial==null&&prefs.contains("$key:position")&&prefs.getInt("$key:position",0)>0)Toast.makeText(this@ReaderActivity,"排版已升级，旧版像素进度无法精确换算，请从目录定位一次",Toast.LENGTH_LONG).show()
            }catch(e:CancellationException){throw e}catch(e:Exception){frame.removeAllViews();val error=col().apply { gravity=Gravity.CENTER;setPadding(dp(32),dp(32),dp(32),dp(32)) };error.add(label("这本书暂时无法打开",21f));error.add(label(e.message?:"请检查文件或重新导入",14f,muted),16);error.add(action("返回书架"){finish()},28);frame.addView(error,FrameLayout.LayoutParams(-1,-1));progress.text="文件保留在本机"}
        }
    }
    private fun updateProgress(){val value=current?:return;val percent=((value.locations.totalProgression?:0.0)*100).coerceIn(0.0,100.0);val name=value.title?.takeIf { it.isNotBlank() }?:bookTitle;caption.text=name;progress.text=String.format(java.util.Locale.CHINA,"%s  ·  %.1f%%",if(scrolling)"滚动阅读" else "轻点中央唤出菜单",percent);progressLabel.text=String.format(java.util.Locale.CHINA,"%s  ·  %.1f%%",name,percent);if(!seek.isPressed)seek.progress=(percent*10).toInt()}
    private fun sheet(title:String,fill:(LinearLayout,Dialog)->Unit){menu?.dismiss();val dialog=Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);val container=col().apply { setPadding(dp(24),dp(18),dp(24),dp(24));background=shape(surface,26) };val handle=View(this).apply { background=shape(muted,3) };container.addView(handle,LinearLayout.LayoutParams(dp(36),dp(4)).apply { gravity=Gravity.CENTER_HORIZONTAL;bottomMargin=dp(22) });val heading=row();heading.addView(label(title,21f),LinearLayout.LayoutParams(0,-2,1f));heading.addView(action("完成"){dialog.dismiss()},LinearLayout.LayoutParams(dp(64),dp(44)));container.add(heading);fill(container,dialog);val viewport=object:ScrollView(this){override fun onMeasure(w:Int,h:Int){super.onMeasure(w,View.MeasureSpec.makeMeasureSpec((resources.displayMetrics.heightPixels*.88).toInt(),View.MeasureSpec.AT_MOST))}};viewport.isFillViewport=false;viewport.addView(container);dialog.setContentView(viewport);dialog.window?.apply { setBackgroundDrawableResource(android.R.color.transparent);setLayout(-1,-2);setGravity(Gravity.BOTTOM);addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);attributes=attributes.apply { dimAmount=.22f } };menu=dialog;dialog.show();dialog.window?.setLayout(-1,-2)}
    private fun items(values:List<String>)=object:ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,values){override fun getView(position:Int,convertView:View?,parent:ViewGroup):View=(super.getView(position,convertView,parent) as TextView).apply { setTextColor(ink);textSize=16f;setPadding(dp(8),dp(14),dp(8),dp(14)) }}
    private fun toc(){val pub=publication?:return;val entries=mutableListOf<Pair<Link,Int>>();fun flatten(links:List<Link>,depth:Int){for(link in links){entries.add(link to depth);flatten(link.children,depth+1)}};flatten(pub.tableOfContents.ifEmpty { pub.readingOrder },0);sheet("目录"){box,d->val list=ListView(this);list.divider=null;list.adapter=items(entries.map { (link,depth)->"　".repeat(depth.coerceAtMost(3))+(link.title?:"章节 ${entries.indexOf(link to depth)+1}") });list.setOnItemClickListener { _,_,i,_->navigator?.go(entries[i].first);d.dismiss();showChrome(false) };box.addView(list,LinearLayout.LayoutParams(-1,dp(360)))}}
    private fun bookmark(){val at=current?:return;val list=runCatching { JSONArray(prefs.getString("$key:bookmarks","[]")) }.getOrDefault(JSONArray());for(i in 0 until list.length())if(list.getJSONObject(i).optString("locator")==at.toJSON().toString()){Toast.makeText(this,"这里已经有书签了",Toast.LENGTH_SHORT).show();return};list.put(JSONObject().put("name",at.title?:bookTitle).put("locator",at.toJSON().toString()).put("progress",at.locations.totalProgression?:0.0));prefs.edit().putString("$key:bookmarks",list.toString()).apply();Toast.makeText(this,"书签已保存",Toast.LENGTH_SHORT).show()}
    private fun bookmarks(){sheet("我的书签"){box,d->val data=runCatching { JSONArray(prefs.getString("$key:bookmarks","[]")) }.getOrDefault(JSONArray());if(data.length()==0){box.add(label("把喜欢的段落，留在这里。",15f,muted),24);box.add(action("添加当前书签"){bookmark();d.dismiss()},24)}else{val list=ListView(this);list.divider=null;list.adapter=items((0 until data.length()).map { val item=data.getJSONObject(it);String.format(java.util.Locale.CHINA,"%s  ·  %.1f%%",item.optString("name"),item.optDouble("progress")*100) });list.setOnItemClickListener { _,_,i,_->runCatching { Locator.fromJSON(JSONObject(data.getJSONObject(i).getString("locator"))) } .getOrNull()?.let { navigator?.go(it) };d.dismiss();showChrome(false) };list.setOnItemLongClickListener { _,_,i,_->data.remove(i);prefs.edit().putString("$key:bookmarks",data.toString()).apply();d.dismiss();bookmarks();true };box.addView(list,LinearLayout.LayoutParams(-1,dp(300)));box.add(label("长按书签可删除",11f,muted),12)}}}
    private fun saveSettings(){prefs.edit().putFloat("readium.font",fontSize.toFloat()).putFloat("readium.line",leading.toFloat()).putFloat("readium.margin",margins.toFloat()).putFloat("readium.paragraph",paragraph.toFloat()).putString("readium.theme",theme).putBoolean("readium.serif",serif).putBoolean("readium.scroll",scrolling).putBoolean("readium.original",original).apply();navigator?.submitPreferences(preferences());root.setBackgroundColor(paper);caption.setTextColor(muted);progress.setTextColor(muted);updateProgress()}
    private fun settings(){sheet("阅读排版"){box,d->
        fun choices(title:String,items:List<Pair<String,()->Unit>>){box.add(label(title,12f,muted),18);val line=row();items.forEach { (name,run)->line.addView(action(name){run();saveSettings();d.dismiss();root.removeView(header);root.removeView(footer);buildChrome();showChrome(true);settings()},LinearLayout.LayoutParams(0,dp(44),1f).apply { if(line.childCount>0)leftMargin=dp(8) }) };box.add(line,8)}
        choices("书页",listOf((if(theme=="white")"✓ 纸白" else "纸白") to {theme="white"},(if(theme=="paper")"✓ 米色" else "米色") to {theme="paper"},(if(theme=="night")"✓ 夜间" else "夜间") to {theme="night"}))
        choices("字体",listOf((if(serif)"✓ 宋体" else "宋体") to {serif=true},(if(!serif)"✓ 黑体" else "黑体") to {serif=false}))
        fun slider(name:String,value:Double,min:Double,max:Double,change:(Double)->Unit){val desc=label(name,13f);box.add(desc,16);val slider=SeekBar(this@ReaderActivity).apply { this.max=100;progress=((value-min)/(max-min)*100).toInt();progressTintList=ColorStateList.valueOf(forest);thumbTintList=ColorStateList.valueOf(forest);contentDescription=name;setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onProgressChanged(v:SeekBar?,p:Int,user:Boolean){if(user){val n=min+(max-min)*p/100;change(n);desc.text=String.format(java.util.Locale.CHINA,"%s  %.2f",name,n)}};override fun onStartTrackingTouch(v:SeekBar?){};override fun onStopTrackingTouch(v:SeekBar?){saveSettings()}}) };box.add(slider,4)}
        val sc=ScrollView(this);val adjustments=col();sc.addView(adjustments);box.addView(sc,LinearLayout.LayoutParams(-1,dp(245)))
        // Keep long accessibility settings inside a scrollable panel on small screens.
        fun range(name:String,value:Double,min:Double,max:Double,change:(Double)->Unit){val before=box.childCount;slider(name,value,min,max,change);val views=(before until box.childCount).map { box.getChildAt(it) };views.forEach { box.removeView(it);adjustments.addView(it) }}
        range("字号",fontSize,.85,1.8){fontSize=it};range("行距",leading,1.35,2.3){leading=it};range("页边距",margins,.6,2.2){margins=it};range("段落间距",paragraph,.2,1.2){paragraph=it}
        val mode=row();mode.addView(action(if(scrolling)"✓ 滚动" else "滚动"){scrolling=true;saveSettings();d.dismiss()},LinearLayout.LayoutParams(0,dp(44),1f));mode.addView(action(if(!scrolling)"✓ 翻页" else "翻页"){scrolling=false;saveSettings();d.dismiss()},LinearLayout.LayoutParams(0,dp(44),1f).apply { leftMargin=dp(8) });box.add(mode,16)
        val publisher=Switch(this).apply { text="使用原书排版";setTextColor(ink);textSize=13f;isChecked=original;setOnCheckedChangeListener { _,checked->original=checked;saveSettings() } };box.add(publisher,14);box.add(label("原书排版开启时，部分间距由图书自身决定。",10f,muted),8)
    }}
    @Deprecated("Platform back callback") override fun onBackPressed(){if(menu?.isShowing==true){menu?.dismiss();return};if(chrome){showChrome(false);return};super.onBackPressed()}
    override fun onPause(){current?.takeIf {ready}?.let { prefs.edit().putString("$key:locator",it.toJSON().toString()).apply() };super.onPause()}
    override fun onDestroy(){menu?.dismiss();super.onDestroy();publication?.close()}
}
