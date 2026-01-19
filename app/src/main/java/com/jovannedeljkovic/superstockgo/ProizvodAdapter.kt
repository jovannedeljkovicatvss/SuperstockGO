package com.jovannedeljkovic.superstockgo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class ProizvodAdapter(
    var proizvodi: List<Proizvod>,
    private val onEditClick: (Proizvod) -> Unit,
    private val onDeleteClick: (Proizvod) -> Unit,
    private val onPlusClick: (Proizvod) -> Unit,
    private val onMinusClick: (Proizvod) -> Unit
) : RecyclerView.Adapter<ProizvodAdapter.ProizvodViewHolder>() {

    class ProizvodViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView.findViewById(R.id.cardView)
        val tvNaziv: TextView = itemView.findViewById(R.id.tvNaziv)
        val tvKategorija: TextView = itemView.findViewById(R.id.tvKategorija)
        val tvKolicina: TextView = itemView.findViewById(R.id.tvKolicina)
        val btnMinus: ImageButton = itemView.findViewById(R.id.btnMinus)
        val btnPlus: ImageButton = itemView.findViewById(R.id.btnPlus)
        val btnEdit: ImageButton = itemView.findViewById(R.id.btnEdit)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProizvodViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_proizvod, parent, false)
        return ProizvodViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProizvodViewHolder, position: Int) {
        val proizvod = proizvodi[position]

        holder.tvNaziv.text = proizvod.naziv
        holder.tvKategorija.text = proizvod.kategorija
        holder.tvKolicina.text = "Količina: ${proizvod.kolicina}"

        // Oboji kartu - KORISTITE ContextCompat.getColor()
        when {
            proizvod.kolicina <= 0 -> {
                holder.cardView.setCardBackgroundColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.red_light)
                )
                holder.tvKolicina.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_dark)
                )
                holder.tvKolicina.text = "❌ NEMA NA STANJU"
            }
            proizvod.kolicina <= 5 -> {
                holder.cardView.setCardBackgroundColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.orange_light)
                )
                holder.tvKolicina.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, android.R.color.holo_orange_dark)
                )
                holder.tvKolicina.text = "⚠️ Niska: ${proizvod.kolicina}"
            }
            else -> {
                holder.cardView.setCardBackgroundColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.green_light)
                )
                holder.tvKolicina.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, android.R.color.black)
                )
            }
        }

        // Postavi klik listenere
        holder.btnMinus.setOnClickListener {
            onMinusClick(proizvod)
        }

        holder.btnPlus.setOnClickListener {
            onPlusClick(proizvod)
        }

        holder.btnEdit.setOnClickListener {
            onEditClick(proizvod)
        }

        holder.btnDelete.setOnClickListener {
            onDeleteClick(proizvod)
        }
    }

    override fun getItemCount(): Int = proizvodi.size

    fun updateData(newProizvodi: List<Proizvod>) {
        proizvodi = newProizvodi
        notifyDataSetChanged()
    }
}