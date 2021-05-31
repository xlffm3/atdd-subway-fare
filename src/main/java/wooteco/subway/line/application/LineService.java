package wooteco.subway.line.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wooteco.subway.exception.badrequest.duplication.LineColorDuplicatedException;
import wooteco.subway.exception.badrequest.duplication.LineNameDuplicatedException;
import wooteco.subway.exception.notfound.LineNotFoundException;
import wooteco.subway.line.dao.LineDao;
import wooteco.subway.line.dao.SectionDao;
import wooteco.subway.line.domain.Line;
import wooteco.subway.line.domain.Section;
import wooteco.subway.line.dto.LineRequest;
import wooteco.subway.line.dto.LineResponse;
import wooteco.subway.line.dto.SectionRequest;
import wooteco.subway.station.application.StationService;
import wooteco.subway.station.domain.Station;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class LineService {

    private final LineDao lineDao;
    private final SectionDao sectionDao;
    private final StationService stationService;

    public LineService(LineDao lineDao, SectionDao sectionDao, StationService stationService) {
        this.lineDao = lineDao;
        this.sectionDao = sectionDao;
        this.stationService = stationService;
    }

    @Transactional
    public LineResponse saveLine(LineRequest request) {
        validateNameDuplication(request.getName());
        validateColorDuplication(request.getColor());
        Line persistLine = lineDao.insert(new Line(request.getName(), request.getColor(), request.getExtraFare()));
        persistLine.addSection(addInitSection(persistLine, request));
        return LineResponse.of(persistLine);
    }

    private void validateNameDuplication(String name) {
        if (lineDao.countLineByName(name) != 0) {
            throw new LineNameDuplicatedException();
        }
    }

    private void validateColorDuplication(String color) {
        if (lineDao.countLineByColor(color) != 0) {
            throw new LineColorDuplicatedException();
        }
    }

    private Section addInitSection(Line line, LineRequest request) {
        if (request.getUpStationId() != null && request.getDownStationId() != null) {
            Station upStation = stationService.findStationById(request.getUpStationId());
            Station downStation = stationService.findStationById(request.getDownStationId());
            Section section = new Section(upStation, downStation, request.getDistance());
            return sectionDao.insert(line, section);
        }
        return null;
    }

    public List<LineResponse> findLineResponses() {
        return LineResponse.listOf(findLines());
    }

    public List<Line> findLines() {
        return lineDao.findAll();
    }

    public LineResponse findLineResponseById(Long id) {
        Line persistLine = findLineById(id);
        return LineResponse.of(persistLine);
    }

    public Line findLineById(Long id) {
        checkIfLineExists(id);
        return lineDao.findById(id);
    }

    private void checkIfLineExists(Long id) {
        if (lineDao.countLineById(id) == 0) {
            throw new LineNotFoundException();
        }
    }

    @Transactional
    public void updateLine(Long id, LineRequest lineUpdateRequest) {
        checkIfLineExists(id);
        validateNameDuplication(lineUpdateRequest.getName());
        validateColorDuplication(lineUpdateRequest.getColor());
        lineDao.update(new Line(id, lineUpdateRequest.getName(), lineUpdateRequest.getColor()));
    }

    @Transactional
    public void deleteLineById(Long id) {
        checkIfLineExists(id);
        lineDao.deleteById(id);
        sectionDao.deleteByLineId(id);
    }

    @Transactional
    public void addLineStation(Long lineId, SectionRequest request) {
        Line line = findLineById(lineId);
        Station upStation = stationService.findStationById(request.getUpStationId());
        Station downStation = stationService.findStationById(request.getDownStationId());
        line.addSection(upStation, downStation, request.getDistance());

        sectionDao.deleteByLineId(lineId);
        sectionDao.insertSections(line);
    }

    @Transactional
    public void removeLineStation(Long lineId, Long stationId) {
        Line line = findLineById(lineId);
        Station station = stationService.findStationById(stationId);
        line.removeSection(station);

        sectionDao.deleteByLineId(lineId);
        sectionDao.insertSections(line);
    }
}
