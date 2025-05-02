package com.guangge.Interview.services;

import com.guangge.Interview.data.Candidates;
import com.guangge.Interview.exception.RestException;
import com.guangge.Interview.record.CandidateRecord;
import com.guangge.Interview.repository.CandidatesRepository;
import com.guangge.Interview.util.Dto2Record;
import com.guangge.Interview.util.ResultCode;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CandidatesService {
    private final CandidatesRepository interviewerRepository;

    public CandidatesService(CandidatesRepository interviewerRepository) {
        this.interviewerRepository = interviewerRepository;
    }

    public Candidates longin(String name, String code) {
        Candidates interviewer = this.interviewerRepository.findByNameAndCode(name, code);
        if (interviewer == null) {
            throw new RestException(String.valueOf(ResultCode.FORBIDDEN.getCode()),"用户名或密码错误");
        }
        return interviewer;
    }

    /**
     * 返回所有候选者信息
     * @return 抽选者信息
     */
    public List<CandidateRecord> getCandidates() {
        List<Candidates> interviewers = this.interviewerRepository.findAll(Sort.by("id").descending());
        return interviewers.stream().map(Dto2Record::toCandidateRecord).toList();
    }

    public Candidates longinAdmin(String userid) {
        Optional<Candidates> interviewerOpt = this.interviewerRepository.findById(Long.valueOf(userid));
        if (!interviewerOpt.isPresent()) {
            throw new RestException(String.valueOf(ResultCode.FORBIDDEN.getCode()),"不存在这个用户");
        }
        return interviewerOpt.get();
    }
}
