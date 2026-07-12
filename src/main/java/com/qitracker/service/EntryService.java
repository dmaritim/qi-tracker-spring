package com.qitracker.service;

import com.qitracker.domain.*;
import com.qitracker.dto.EntryDtos.EntryRequest;
import com.qitracker.dto.EntryDtos.EntryResponse;
import com.qitracker.dto.EntryDtos.EntryValueDto;
import com.qitracker.exception.ApiException;
import com.qitracker.repository.EntryRepository;
import com.qitracker.repository.EntryValueRepository;
import com.qitracker.repository.IndicatorElementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EntryService {

    private final EntryRepository entryRepository;
    private final EntryValueRepository entryValueRepository;
    private final IndicatorElementRepository elementRepository;
    private final IndicatorService indicatorService;
    private final ProjectAccessService accessService;

    public EntryService(EntryRepository entryRepository, EntryValueRepository entryValueRepository,
                         IndicatorElementRepository elementRepository, IndicatorService indicatorService,
                         ProjectAccessService accessService) {
        this.entryRepository = entryRepository;
        this.entryValueRepository = entryValueRepository;
        this.elementRepository = elementRepository;
        this.indicatorService = indicatorService;
        this.accessService = accessService;
    }
    
    @Transactional(readOnly = true)
    public List<EntryResponse> listForIndicator(Long indicatorId) {
        return entryRepository.findByIndicatorIdOrderByEntryDateAsc(indicatorId).stream().map(this::toResponse).toList();
    }

    /** The authoritative formula: (numerator elements, signed, summed) [/ (denominator elements, signed, summed)] x multiplier. */
    public double computeValue(Indicator indicator, Map<Long, Double> amountsByElementId) {
        List<IndicatorElement> elements = elementRepository.findByIndicatorId(indicator.getId());
        double numeratorSum = 0;
        double denominatorSum = 0;
        boolean hasDenominator = false;
        for (IndicatorElement el : elements) {
            double amount = amountsByElementId.getOrDefault(el.getId(), 0.0);
            double signed = el.getSign() == ElementSign.SUBTRACT ? -amount : amount;
            if (el.getSection() == ElementSection.NUMERATOR) {
                numeratorSum += signed;
            } else {
                denominatorSum += signed;
                hasDenominator = true;
            }
        }
        double multiplier = indicator.getMultiplier() == null ? 1.0 : indicator.getMultiplier();
        if (!hasDenominator) return round2(numeratorSum * multiplier);
        if (denominatorSum == 0) return 0.0;
        return round2((numeratorSum / denominatorSum) * multiplier);
    }

    @Transactional
    public EntryResponse create(Long indicatorId, User requester, EntryRequest req) {
        Indicator indicator = indicatorService.getOrThrow(indicatorId);
        accessService.requireMember(indicator.getProject(), requester);
        if (req.date() == null || req.date().isBlank()) throw ApiException.badRequest("Date is required.");
        LocalDate date;
        try { date = LocalDate.parse(req.date()); } catch (Exception e) { throw ApiException.badRequest("Invalid date."); }

        Map<Long, Double> amounts = new HashMap<>();
        if (req.values() != null) {
            for (EntryValueDto v : req.values()) amounts.put(v.elementId(), v.amount());
        }
        double computed = computeValue(indicator, amounts);

        Entry entry = Entry.builder()
            .indicator(indicator).entryDate(date).computedValue(computed)
            .note(req.note()).createdBy(requester).build();
        final Entry savedEntry = entryRepository.save(entry);

        List<IndicatorElement> elements = elementRepository.findByIndicatorId(indicatorId);
        List<EntryValue> values = elements.stream()
            .map(el -> EntryValue.builder().entry(savedEntry).indicatorElement(el)
                .value(amounts.getOrDefault(el.getId(), 0.0)).build())
            .toList();
        entryValueRepository.saveAll(values);

        return toResponse(savedEntry);
    }

    @Transactional
    public EntryResponse update(Long entryId, User requester, EntryRequest req) {
        Entry entry = entryRepository.findById(entryId).orElseThrow(() -> ApiException.notFound("Entry not found."));
        Project project = entry.getIndicator().getProject();
        boolean canEdit = accessService.isCreator(project, requester)
            || (entry.getCreatedBy() != null && entry.getCreatedBy().getId().equals(requester.getId()));
        if (!canEdit) throw ApiException.forbidden("You can only edit entries you logged yourself, unless you created the project.");

        if (req.date() == null || req.date().isBlank()) throw ApiException.badRequest("Date is required.");
        LocalDate date;
        try { date = LocalDate.parse(req.date()); } catch (Exception e) { throw ApiException.badRequest("Invalid date."); }

        Map<Long, Double> amounts = new HashMap<>();
        if (req.values() != null) {
            for (EntryValueDto v : req.values()) amounts.put(v.elementId(), v.amount());
        }
        double computed = computeValue(entry.getIndicator(), amounts);

        entry.setEntryDate(date);
        entry.setComputedValue(computed);
        entry.setNote(req.note());
        entryRepository.save(entry);

        entryValueRepository.deleteByEntryId(entry.getId());
        List<IndicatorElement> elements = elementRepository.findByIndicatorId(entry.getIndicator().getId());
        List<EntryValue> values = elements.stream()
            .map(el -> EntryValue.builder().entry(entry).indicatorElement(el)
                .value(amounts.getOrDefault(el.getId(), 0.0)).build())
            .toList();
        entryValueRepository.saveAll(values);

        return toResponse(entry);
    }

    @Transactional
    public void delete(Long entryId, User requester) {
        Entry entry = entryRepository.findById(entryId).orElseThrow(() -> ApiException.notFound("Entry not found."));
        Project project = entry.getIndicator().getProject();
        boolean canDelete = accessService.isCreator(project, requester)
            || (entry.getCreatedBy() != null && entry.getCreatedBy().getId().equals(requester.getId()));
        if (!canDelete) throw ApiException.forbidden("You can only remove entries you logged yourself, unless you created the project.");
        entryRepository.delete(entry);
    }

    private double round2(double n) { return Math.round(n * 100.0) / 100.0; }

    public EntryResponse toResponse(Entry entry) {
        List<EntryValue> values = entryValueRepository.findByEntryId(entry.getId());
        List<EntryValueDto> valueDtos = values.stream()
            .map(v -> new EntryValueDto(v.getIndicatorElement().getId(), v.getValue())).toList();
        return new EntryResponse(
            entry.getId(), entry.getIndicator().getId(), entry.getEntryDate().toString(),
            valueDtos, entry.getComputedValue(), entry.getNote(),
            entry.getCreatedBy() == null ? null : entry.getCreatedBy().getId(),
            entry.getCreatedBy() == null ? null : entry.getCreatedBy().getName(),
            entry.getCreatedAt()
        );
    }
}
