package ir.hammeydan.app

import android.content.Context
import android.graphics.*
import android.view.*
import org.json.JSONArray
import kotlin.math.*

/** Offline geographic overview. No location permissions or live person tracking. */
class CityMap(context:Context,dark:Boolean,private val selected:(String)->Unit):View(context){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline=Path()
    private val land=Color.parseColor(if(dark)"#345B48" else "#D5E9DB")
    private val water=Color.parseColor(if(dark)"#182F2A" else "#EAF3F5")
    private val edge=Color.parseColor(if(dark)"#709881" else "#7B9E8B")
    private val ink=Color.parseColor(if(dark)"#F2F7F4" else "#224C3D")
    private val accent=Color.parseColor(if(dark)"#99E0B5" else "#12644B")
    private val density=resources.displayMetrics.density
    private var zoom=1f;private var tx=0f;private var ty=0f;private var active=""
    private data class City(val name:String,val lon:Double,val lat:Double)
    private val cities=listOf(City("تهران",51.389,35.689),City("مشهد",59.606,36.297),City("اصفهان",51.668,32.654),City("شیراز",52.583,29.591),City("تبریز",46.292,38.08),City("اهواز",48.670,31.318),City("کرمان",57.079,30.284),City("رشت",49.583,37.28),City("بندرعباس",56.267,27.183),City("یزد",54.367,31.897),City("همدان",48.516,34.799),City("کرمانشاه",47.066,34.314))
    init { contentDescription="نقشه کلی ایران و شهرهای اصلی";isClickable=true;runCatching {val a=JSONArray(context.assets.open("iran_outline.json").bufferedReader().use{it.readText()});for(i in 0 until a.length()){val poly=a.getJSONArray(i);for(j in 0 until poly.length()){val pt=poly.getJSONArray(j);val x=pt.getDouble(0).toFloat();val y=-pt.getDouble(1).toFloat();if(j==0)outline.moveTo(x,y)else outline.lineTo(x,y)};outline.close()}} }
    private val scale=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){override fun onScale(d:ScaleGestureDetector):Boolean{val old=zoom;zoom=(zoom*d.scaleFactor).coerceIn(1f,4f);val f=zoom/old;tx=d.focusX-(d.focusX-tx)*f;ty=d.focusY-(d.focusY-ty)*f;limit();invalidate();return true}})
    private val gesture=GestureDetector(context,object:GestureDetector.SimpleOnGestureListener(){override fun onDown(e:MotionEvent)=true;override fun onScroll(e1:MotionEvent?,e2:MotionEvent,distanceX:Float,distanceY:Float):Boolean{if(zoom>1&&!scale.isInProgress){tx-=distanceX;ty-=distanceY;limit();invalidate()};return true};override fun onDoubleTap(e:MotionEvent):Boolean{zoom=1f;tx=0f;ty=0f;invalidate();return true};override fun onSingleTapUp(e:MotionEvent):Boolean{val nearest=cities.minByOrNull{hypot(e.x-sx(it.lon),e.y-sy(it.lat))};if(nearest!=null&&hypot(e.x-sx(nearest.lon),e.y-sy(nearest.lat))<26*density){active=nearest.name;selected(active);invalidate();performClick()};return true}})
    private fun base()=min(width/20f,height/16f).coerceAtLeast(.01f)
    private fun sx(lon:Double)=((lon-53.7).toFloat()*base()+width/2f)*zoom+tx
    private fun sy(lat:Double)=((32.4-lat).toFloat()*base()+height/2f)*zoom+ty
    private fun limit(){tx=tx.coerceIn(-width*(zoom-1),0f);ty=ty.coerceIn(-height*(zoom-1),0f)}
    override fun onDraw(canvas:Canvas){super.onDraw(canvas);canvas.drawColor(water);canvas.save();canvas.translate(sx(0.0),sy(0.0));canvas.scale(base()*zoom,base()*zoom);paint.color=land;paint.style=Paint.Style.FILL;canvas.drawPath(outline,paint);paint.color=edge;paint.style=Paint.Style.STROKE;paint.strokeWidth=1.2f*density/(base()*zoom);canvas.drawPath(outline,paint);canvas.restore();paint.style=Paint.Style.FILL;paint.textSize=10*density;paint.textAlign=Paint.Align.CENTER;cities.forEach{val x=sx(it.lon);val y=sy(it.lat);paint.color=if(active==it.name)Color.parseColor("#D64249")else accent;canvas.drawCircle(x,y,(if(active==it.name)5f else 3.2f)*density,paint);paint.color=ink;canvas.drawText(it.name,x,y-6*density,paint)};paint.textAlign=Paint.Align.LEFT;paint.textSize=9*density;canvas.drawText("Natural Earth · نقشه کلی",8*density,height-8*density,paint)}
    override fun onTouchEvent(event:MotionEvent):Boolean{parent?.requestDisallowInterceptTouchEvent(event.actionMasked!=MotionEvent.ACTION_UP&&event.actionMasked!=MotionEvent.ACTION_CANCEL);scale.onTouchEvent(event);gesture.onTouchEvent(event);return true}
    override fun performClick():Boolean{super.performClick();return true}
}
