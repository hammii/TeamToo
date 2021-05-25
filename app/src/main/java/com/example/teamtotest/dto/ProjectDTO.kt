package com.example.teamtotest.dto

data class ProjectDTO (
    val projectName : String = "",
    var progressData : ProgressDTO ?= null,
    var pid : String? = null
)