package com.cameraflash


import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.adapter_row.view.*
import java.util.*


// The adapter class which
// extends RecyclerView Adapter
public class AdapterClass (val type:Int,private var mContext: Context?, var listValues: ArrayList<ModelClass>,var intrfc:SetValues) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    val ISO_VALUES=0
    val SHUTTER_VALUES=1




        override fun getItemViewType(position: Int): Int {
            if (type==0)
                return ISO_VALUES;
             else
                return SHUTTER_VALUES;

            return -1;
        }



    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val viewHolder: RecyclerView.ViewHolder
        val inflater = LayoutInflater.from(viewGroup.getContext())

        when (viewType) {
            ISO_VALUES -> {
                val v1: View = inflater.inflate(R.layout.adapter_row, viewGroup, false)
                viewHolder = ISOViewHolder(v1)
            }
            SHUTTER_VALUES -> {
                val v2: View = inflater.inflate(R.layout.adapter_row, viewGroup, false)
                viewHolder = ShutterViewHolder(v2)
            }
            else -> {
                val v = inflater.inflate(R.layout.adapter_row, viewGroup, false)
                viewHolder = ISOViewHolder(v)
            }
        }
        return viewHolder
    }

    override fun getItemCount(): Int {
      return listValues.size
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        when (viewHolder.getItemViewType()) {
            ISO_VALUES -> {
                val iso: ISOViewHolder = viewHolder as ISOViewHolder
                iso.bind(listValues.get(position),intrfc,type,position,mContext!!)
            }
            SHUTTER_VALUES -> {
                val shutter: ShutterViewHolder = viewHolder as ShutterViewHolder
                shutter.bind(listValues.get(position),intrfc,type,position,mContext!!)
            }

        }
    }

    class ISOViewHolder(itemView:View) : RecyclerView.ViewHolder(itemView) {
        fun bind(model:ModelClass,interfc:SetValues,TYPE:Int,pos:Int,context:Context)
        {
            itemView.textValue.setText(model.value)
            if(model.isSelected)
            {
                itemView.textValue.setTextColor(context.resources.getColor(R.color.purple_200))
            }
            else
            {
                itemView.textValue.setTextColor(context.resources.getColor(R.color.white))
            }
            itemView.setOnClickListener {
                interfc.returnValue(TYPE,model.value,model,pos)

            }
        }
    }

    class ShutterViewHolder(itemView:View) : RecyclerView.ViewHolder(itemView) {
        fun bind(model:ModelClass,interfc:SetValues,TYPE:Int,pos:Int,context:Context)
        {
            if(model.isSelected)
            {
                itemView.textValue.setTextColor(context.resources.getColor(R.color.purple_200))
            }
            else
            {
                itemView.textValue.setTextColor(context.resources.getColor(R.color.white))
            }
            itemView.textValue.setText(model.value)
            itemView.setOnClickListener {  interfc.returnValue(TYPE,model.value,model,pos) }

        }
    }
}



