package com.qitracker.domain;

// Lowercase constant names so EnumType.STRING persists exactly what the
// schema comment documents ('daily' | 'weekly' | 'biweekly' | 'monthly' | 'quarterly')
// and what the frontend already sends/expects.
public enum ReportingFrequency { daily, weekly, biweekly, monthly, quarterly }
